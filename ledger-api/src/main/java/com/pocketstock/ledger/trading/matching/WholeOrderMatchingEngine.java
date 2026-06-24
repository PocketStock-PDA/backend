package com.pocketstock.ledger.trading.matching;

import com.pocketstock.ledger.config.RealtimeSubscriptionManager;
import com.pocketstock.ledger.kis.KisAskingPriceResponse;
import com.pocketstock.ledger.kis.KisMarketClient;
import com.pocketstock.ledger.kis.KisRealtimeClient;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import com.pocketstock.ledger.ls.LsRealtimeClient;
import com.pocketstock.ledger.realtime.RealtimeReconnectedEvent;
import com.pocketstock.ledger.trading.client.LsMarketClient;
import com.pocketstock.ledger.trading.client.LsT8450Response;
import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.support.BookWalker;
import com.pocketstock.ledger.trading.support.OverseasMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;

/**
 * 온주 지정가 PENDING 매칭 엔진. 실시간 호가 틱을 받아 종목별 PENDING과 cross 판정,
 * 닿으면 사다리 훑어 전량 가능 시 체결시킨다(부분체결 없음 → 잔량 부족이면 PENDING 유지).
 * 국내(LS UH1)·해외(KIS HDFSASP0)를 한 엔진이 처리한다 — 벤더 차이는 리스너가 흡수하고
 * 여긴 정규화된 {@link QuoteTick}(국내 {@link DomesticQuoteTick}·해외 {@link ForeignQuoteTick})만 받는다.
 *
 * <p>구독 = PENDING 생명주기(온디맨드): 종목 첫 PENDING이면 호가 구독 ON, 마지막이 종료되면 OFF.
 * 국내/해외 구분은 종목별 PENDING 스냅샷({@code exchange})이 들고 있어 구독 acquire/release를 분기한다.
 * 인덱스({@code 종목→PENDING})는 매칭용 캐시일 뿐 SSOT는 DB({@code orders.status=PENDING}) —
 * 부팅 시 DB에서 재적재한다. 체결/취소의 실제 정합성 가드는 {@link PendingFillService}의 조건부 전이.
 *
 * <p>틱은 LS·KIS 두 상류 세션 스레드에서 각각 호출되지만 국내·해외 종목은 키가 겹치지 않아
 * 같은 종목에 대한 동시 호출은 없다(인덱스는 {@link ConcurrentHashMap}). 취소(다른 스레드)와의
 * 경합은 {@link PendingFillService}의 조건부 전이가 최종 차단하고, 인덱스 정리는 최선노력(다음 틱 자가복구).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WholeOrderMatchingEngine {

    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");
    private static final Set<String> SUPPORTED_EXCHANGES =
            Stream.concat(DOMESTIC_EXCHANGES.stream(), OVERSEAS_EXCHANGES.stream())
                    .collect(Collectors.toUnmodifiableSet());

    private final OrderMapper orderMapper;
    private final RealtimeSubscriptionManager subscriptionManager;
    private final PendingFillService pendingFillService;
    private final LsMarketClient lsMarketClient;
    private final KisMarketClient kisMarketClient;
    private final LsRealtimeClient lsRealtimeClient;
    private final KisRealtimeClient kisRealtimeClient;
    private final LedgerActivation activation;

    /** stockCode → (orderId → PENDING 스냅샷). 매칭용 캐시(SSOT=DB). */
    private final Map<String, Map<Long, Pending>> index = new ConcurrentHashMap<>();

    /** 부팅 복구 — DB의 국내·해외 PENDING 전건을 인덱스에 재적재하고 종목별 호가 구독을 켠다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        reindexAndArm();
    }

    /**
     * PENDING 인덱스를 DB(SSOT)로부터 재적재하고, 활성 색이면 종목별 호가 구독 ON + 부팅 스냅샷 보정까지 무장.
     * 부팅 시 1회, Blue-Green rearm 시 재호출 — rearm 때는 옛 색(다른 인스턴스)에 들어온 PENDING도
     * DB 기준으로 다시 담아 "새 색 인덱스에 없어 영영 미매칭되는 고아 PENDING"을 막는다.
     * 비활성 색은 인덱스만 채우고 구독·스냅샷은 보류(LS/KIS 세션 안 엶) → rearm 때 무장.
     */
    public synchronized void reindexAndArm() {
        List<Order> pendings = orderMapper.findPendingByExchanges(SUPPORTED_EXCHANGES);
        index.clear();
        for (Order o : pendings) {
            put(o.getStockCode(), toPending(o));
        }
        if (!activation.isActive()) {
            log.info("온주 지정가 매칭 — PENDING {}건 인덱스 적재(비활성 색 — 구독·스냅샷 보류)", pendings.size());
            return;
        }
        // 종목별 1회만 구독(같은 종목 PENDING은 거래소가 동일) — 인덱스의 아무 PENDING에서 거래소 파생.
        index.forEach((stockCode, orders) -> {
            String exchange = anyExchange(orders);
            if (exchange != null) {
                acquireQuote(stockCode, exchange);
            }
        });
        log.info("온주 지정가 매칭 — PENDING {}건 재적재, 종목 {}개 호가 구독 ON", pendings.size(), index.size());
        // 서버 다운 동안 닿았을 cross를 부팅 시 REST 호가 1회 스냅샷으로 보정(국내·해외 전부).
        sweepSnapshot(e -> true);
    }

    /**
     * WS 단절→재연결 보정 — 끊긴 동안 실시간 틱을 놓쳤을 수 있으니 PENDING 종목별 REST 호가를
     * 1회 스냅샷해 재매칭한다(폴링 루프 아님, 재연결 1발). LS 재연결은 국내 PENDING만, KIS 재연결은
     * 해외 PENDING만 보정한다(끊긴 세션의 종목만).
     */
    @EventListener
    public void onReconnect(RealtimeReconnectedEvent e) {
        Predicate<String> filter;
        if ("LS".equals(e.broker())) {
            filter = DOMESTIC_EXCHANGES::contains;
        } else if ("KIS".equals(e.broker())) {
            filter = OVERSEAS_EXCHANGES::contains;
        } else {
            return;
        }
        log.info("{} 실시간 재연결 — 해당 PENDING 종목 REST 스냅샷 보정", e.broker());
        sweepSnapshot(filter);
    }

    /**
     * PENDING 구독 생명관리(옵션 B, #127) — 15초마다 PENDING이 걸린 상류 세션이 살아있는지 확인하고
     * 끊겼으면 재연결을 유도한다. {@code reconnectIfStale}가 재연결하면 activeKeys 재등록 +
     * {@link RealtimeReconnectedEvent} 발행 → {@link #onReconnect}가 끊긴 동안의 cross를 REST
     * 스냅샷으로 보정한다. PENDING이 없으면 세션을 억지로 살릴 이유가 없어 아무것도 안 한다.
     *
     * <p>국내·해외 공용으로 환율 핀 무임승차를 끊는다 — 종전엔 LS 세션 복구가 환율 도메인의
     * {@code CurrencyRatePinner} 1분 하트비트에 얹혀 있었고(트레이딩↔환율 결합·복구지연 ≤60초),
     * 해외(KIS)는 그런 핀이 없어 자동 복구가 아예 없었다(조용한 미체결). 매칭 엔진이 자기 PENDING의
     * 세션을 직접 챙겨 그 의존을 끊고 해외 구멍도 메운다.
     *
     * <p>해외는 추가로 구독을 멱등 재무장한다 — 장 마감 중 생성된 PENDING은 {@code resolveForeign}이
     * CLOSED라 스킵해 activeKeys에 없으므로, 개장 후 재무장해야 재연결 시 함께 재등록된다.
     * (국내는 마감 무관하게 항상 등록돼 activeKeys에 있어 재연결만으로 충분.)
     */
    @Scheduled(initialDelay = 15_000, fixedDelay = 15_000)
    public void keepPendingSubscriptionsAlive() {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 상류 세션 재연결/재무장 안 함(LS/KIS 세션 안 엶)
        }
        boolean hasDomestic = false;
        List<String> foreignStocks = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Pending>> entry : index.entrySet()) {
            String exchange = anyExchange(entry.getValue());
            if (exchange == null) {
                continue;
            }
            if (OVERSEAS_EXCHANGES.contains(exchange)) {
                foreignStocks.add(entry.getKey());
            } else if (DOMESTIC_EXCHANGES.contains(exchange)) {
                hasDomestic = true;
            }
        }
        // 끊긴 세션을 먼저 되살린다(reconnectIfStale → activeKeys 재등록 + onReconnect 스냅샷 보정).
        if (!foreignStocks.isEmpty()) {
            kisRealtimeClient.reconnectIfStale();
        }
        if (hasDomestic) {
            lsRealtimeClient.reconnectIfStale();
        }
        // 해외만: 장 마감 중 스킵돼 activeKeys에 없던 PENDING을 멱등 재무장(개장 후 등록 복구).
        for (String stockCode : foreignStocks) {
            try {
                subscriptionManager.acquireForeignQuote(stockCode);
                // 검사~재무장 사이 마지막 PENDING이 종료됐으면(TOCTOU) 방금 켠 구독을 되돌려 누수 방지.
                if (!index.containsKey(stockCode)) {
                    subscriptionManager.releaseForeignQuote(stockCode);
                }
            } catch (Exception e) {
                log.warn("해외 PENDING 재무장 실패 stockCode={} — 다음 주기 재시도: {}", stockCode, e.getMessage());
            }
        }
    }

    /** PENDING 종목 중 거래소 필터에 맞는 것만 REST 호가 스냅샷 1회 → 매칭(부팅·재연결 공용). */
    private void sweepSnapshot(Predicate<String> exchangeFilter) {
        for (Map.Entry<String, Map<Long, Pending>> entry : index.entrySet()) {
            String stockCode = entry.getKey();
            String exchange = anyExchange(entry.getValue());
            if (exchange == null || !exchangeFilter.test(exchange)) {
                continue;
            }
            try {
                onTick(snapshot(stockCode, exchange));
            } catch (Exception ex) {
                log.warn("REST 호가 스냅샷 보정 실패 stockCode={} exchange={} — 다음 틱/재연결 재시도",
                        stockCode, exchange, ex);
            }
        }
    }

    /** 거래소별 REST 호가 1틱 — 국내 LS T8450 / 해외 KIS HHDFS76200100(정규장 EXCD, 즉시체결 경로와 동일). */
    private QuoteTick snapshot(String stockCode, String exchange) {
        if (OVERSEAS_EXCHANGES.contains(exchange)) {
            String excd = OverseasMarket.fromExchange(exchange).regularCode();
            KisAskingPriceResponse.Output2 o2 = kisMarketClient.getOverseasOrderbook(excd, stockCode).output2();
            return new ForeignQuoteTick(stockCode,
                    toDec(o2.askPrices()), toDec(o2.askVolumes()),
                    toDec(o2.bidPrices()), toDec(o2.bidVolumes()));
        }
        LsT8450Response.OutBlock ob = lsMarketClient.getDomesticOrderbook(stockCode);
        return new DomesticQuoteTick(stockCode,
                toDec(ob.askPrices()), toDec(ob.askVolumes()),
                toDec(ob.bidPrices()), toDec(ob.bidVolumes()));
    }

    /** PENDING 진입(커밋 후) — 인덱스 등록 + 그 종목 첫 PENDING이면 호가 구독 ON. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPendingCreated(PendingOrderCreatedEvent e) {
        if (!SUPPORTED_EXCHANGES.contains(e.exchange())) {
            return;   // 지원하지 않는 거래소(소수점 등)는 데몬 대상 아님
        }
        boolean firstForStock = put(e.stockCode(), new Pending(
                e.orderId(), e.userId(), e.accountId(), e.exchange(), e.side(),
                e.limitPrice(), e.quantity(), e.currency()));
        if (firstForStock) {
            acquireQuote(e.stockCode(), e.exchange());
        }
    }

    /** PENDING 종료(취소 커밋 후) — 인덱스 제거 + 그 종목 PENDING 0건이면 호가 구독 OFF. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPendingClosed(PendingOrderClosedEvent e) {
        if (remove(e.stockCode(), e.orderId())) {
            releaseQuote(e.stockCode(), e.exchange());
        }
    }

    /**
     * 호가 틱 — 그 종목 PENDING들과 cross 판정, 닿으면 사다리 훑어 전량 가능 시 체결.
     * 국내·해외 틱을 한 메서드로 받는다({@link QuoteTick}). 종목 내 틱은 한 상류 세션 스레드에서
     * 순차 호출되므로 직렬. 취소(다른 스레드)와의 경합은 {@link PendingFillService}의 조건부 전이가
     * 최종 차단하고, 인덱스 정리는 최선노력(다음 틱 자가복구).
     */
    @EventListener
    public void onTick(QuoteTick t) {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 매칭 실행 안 함(중복 체결 방지). 활성 색이 체결.
        }
        Map<Long, Pending> orders = index.get(t.stockCode());
        if (orders == null) {
            return;
        }
        for (Pending p : orders.values()) {   // ConcurrentHashMap weakly-consistent 순회
            if (tryMatch(t, p) && remove(t.stockCode(), p.orderId())) {
                releaseQuote(t.stockCode(), p.exchange());   // 마지막 PENDING 체결 → 구독 OFF
            }
        }
    }

    /** @return true=인덱스에서 제거 대상(체결됨 또는 이미 종결). false=유지(미도달·잔량부족·일시오류 재시도). */
    private boolean tryMatch(QuoteTick t, Pending p) {
        boolean buy = "BUY".equals(p.side());
        BigDecimal best = buy ? first(t.askPrices()) : first(t.bidPrices());
        if (best.signum() <= 0) {
            return false;   // 반대편 호가 없음
        }
        // 즉시성 판정 = 반대편 최우선 호가. 매수: 최우선 매도호가 ≤ 지정가, 매도: 최우선 매수호가 ≥ 지정가.
        boolean cross = buy ? best.compareTo(p.limitPrice()) <= 0 : best.compareTo(p.limitPrice()) >= 0;
        if (!cross) {
            return false;   // 아직 지정가 미도달 → PENDING 유지
        }
        BigDecimal[] prices = buy ? t.askPrices() : t.bidPrices();
        BigDecimal[] volumes = buy ? t.askVolumes() : t.bidVolumes();
        BookWalker.Fill f = BookWalker.walk(prices, volumes, p.quantity(), p.limitPrice(), buy);
        if (!f.complete()) {
            return false;   // 닿았어도 잔량 부족 → 부분체결 없음, PENDING 유지
        }
        try {
            boolean filled = pendingFillService.fill(new PendingFillService.PendingFill(
                    p.orderId(), p.userId(), p.accountId(), t.stockCode(),
                    p.side(), p.limitPrice(), p.quantity(), p.currency(), f.avgPrice()));
            if (filled) {
                log.info("온주 지정가 체결 orderId={} {} {}주 @ {}", p.orderId(), p.side(), p.quantity(), f.avgPrice());
            }
            return true;   // 체결됨이든(filled) 경합에 짐이든(이미 종결) 인덱스에서 제거
        } catch (Exception ex) {
            log.error("온주 지정가 체결 실패 orderId={} — 다음 틱 재시도", p.orderId(), ex);
            return false;
        }
    }

    // ---- 구독 dispatch(국내 LS UH1 / 해외 KIS HDFSASP0) ----

    private void acquireQuote(String stockCode, String exchange) {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 구독 보류(refCount 미증가). rearm 시 reindexAndArm 이 일괄 acquire.
        }
        if (OVERSEAS_EXCHANGES.contains(exchange)) {
            subscriptionManager.acquireForeignQuote(stockCode);
        } else {
            subscriptionManager.acquireAsking(stockCode);
        }
    }

    private void releaseQuote(String stockCode, String exchange) {
        if (OVERSEAS_EXCHANGES.contains(exchange)) {
            subscriptionManager.releaseForeignQuote(stockCode);
        } else {
            subscriptionManager.releaseAsking(stockCode);
        }
    }

    // ---- 인덱스(종목→PENDING) ----

    /** @return 이 종목의 첫 PENDING이면 true(구독 ON 트리거). */
    private boolean put(String stockCode, Pending p) {
        boolean[] first = {false};
        Map<Long, Pending> m = index.computeIfAbsent(stockCode, k -> {
            first[0] = true;
            return new ConcurrentHashMap<>();
        });
        m.put(p.orderId(), p);
        return first[0];
    }

    /** @return 제거로 그 종목 PENDING이 0건이 되면 true(구독 OFF 트리거). */
    private boolean remove(String stockCode, Long orderId) {
        boolean[] becameEmpty = {false};
        // compute로 원자 처리 — isEmpty와 매핑 제거 사이에 put(computeIfAbsent)이 끼어 새 주문이
        // 통째로 유실되는 레이스 차단(같은 키의 compute/computeIfAbsent는 ConcurrentHashMap이 직렬화).
        index.computeIfPresent(stockCode, (code, m) -> {
            m.remove(orderId);
            if (m.isEmpty()) {
                becameEmpty[0] = true;
                return null;   // 매핑 원자 제거
            }
            return m;
        });
        return becameEmpty[0];
    }

    private Pending toPending(Order o) {
        return new Pending(o.getId(), o.getUserId(), o.getAccountId(), o.getExchange(), o.getSide(),
                o.getPrice(), o.getOrderQuantity(), o.getCurrency());
    }

    /** 종목의 PENDING 중 아무 것의 거래소(같은 종목이면 모두 동일). 비었으면 null. */
    private static String anyExchange(Map<Long, Pending> orders) {
        for (Pending p : orders.values()) {
            return p.exchange();
        }
        return null;
    }

    private static BigDecimal first(BigDecimal[] arr) {
        return arr != null && arr.length > 0 && arr[0] != null ? arr[0] : BigDecimal.ZERO;
    }

    /** long[] 호가/잔량 → BigDecimal[](국내 LS REST 스냅샷 매핑). */
    private static BigDecimal[] toDec(long[] arr) {
        BigDecimal[] out = new BigDecimal[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = BigDecimal.valueOf(arr[i]);
        }
        return out;
    }

    /** String[] 호가/잔량 → BigDecimal[](해외 KIS REST 스냅샷 매핑, 빈 값 0). */
    private static BigDecimal[] toDec(String[] arr) {
        BigDecimal[] out = new BigDecimal[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = dec(arr[i]);
        }
        return out;
    }

    /** 매칭에 필요한 PENDING 주문 스냅샷(돈 이동 권한은 DB 조건부 전이가 최종 검증). */
    private record Pending(Long orderId, Long userId, Long accountId, String exchange, String side,
                           BigDecimal limitPrice, BigDecimal quantity, String currency) {
    }
}
