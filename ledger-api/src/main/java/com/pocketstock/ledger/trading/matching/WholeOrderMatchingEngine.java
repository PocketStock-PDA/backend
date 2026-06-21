package com.pocketstock.ledger.trading.matching;

import com.pocketstock.ledger.config.RealtimeSubscriptionManager;
import com.pocketstock.ledger.realtime.RealtimeReconnectedEvent;
import com.pocketstock.ledger.trading.client.LsMarketClient;
import com.pocketstock.ledger.trading.client.LsT8450Response;
import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.support.BookWalker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 온주 지정가 PENDING 매칭 엔진(국내 LS). 실시간 호가 틱을 받아 종목별 PENDING과 cross 판정,
 * 닿으면 사다리 훑어 전량 가능 시 체결시킨다(부분체결 없음 → 잔량 부족이면 PENDING 유지).
 *
 * <p>구독 = PENDING 생명주기(온디맨드): 종목 첫 PENDING이면 호가 구독 ON, 마지막이 종료되면 OFF.
 * 인덱스({@code 종목→PENDING})는 매칭용 캐시일 뿐 SSOT는 DB({@code orders.status=PENDING}) —
 * 부팅 시 DB에서 재적재한다. 체결/취소의 실제 정합성 가드는 {@link PendingFillService}의 조건부 전이.
 *
 * <p>해외(KIS)는 RSYM 키 매핑이 별도라 후속 범위 — 지금은 국내 거래소만 인덱싱한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WholeOrderMatchingEngine {

    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");

    private final OrderMapper orderMapper;
    private final RealtimeSubscriptionManager subscriptionManager;
    private final PendingFillService pendingFillService;
    private final LsMarketClient lsMarketClient;

    /** stockCode → (orderId → PENDING 스냅샷). 매칭용 캐시(SSOT=DB). */
    private final Map<String, Map<Long, Pending>> index = new ConcurrentHashMap<>();

    /** 부팅 복구 — DB의 국내 PENDING 전건을 인덱스에 재적재하고 종목별 호가 구독을 켠다. */
    @EventListener(ApplicationReadyEvent.class)
    public void reloadPending() {
        List<Order> pendings = orderMapper.findPendingByExchanges(DOMESTIC_EXCHANGES);
        for (Order o : pendings) {
            put(o.getStockCode(), toPending(o));
        }
        index.keySet().forEach(subscriptionManager::acquireAsking);
        log.info("온주 지정가 매칭 — PENDING {}건 재적재, 종목 {}개 호가 구독 ON", pendings.size(), index.size());
        // 서버 다운 동안 닿았을 cross를 부팅 시 REST 호가 1회 스냅샷으로 보정.
        sweepSnapshot();
    }

    /**
     * WS 단절→재연결 보정 — 끊긴 동안 실시간 틱을 놓쳤을 수 있으니 PENDING 종목별 REST 호가를
     * 1회 스냅샷해 재매칭한다(폴링 루프 아님, 재연결 1발). LS만 — 해외는 후속 범위.
     */
    @EventListener
    public void onReconnect(RealtimeReconnectedEvent e) {
        if (!"LS".equals(e.broker())) {
            return;
        }
        log.info("LS 실시간 재연결 — PENDING {}종목 REST 스냅샷 보정", index.size());
        sweepSnapshot();
    }

    /** PENDING 종목 전부에 대해 REST 호가 스냅샷 1회 → 매칭(부팅·재연결 공용). */
    private void sweepSnapshot() {
        for (String stockCode : index.keySet()) {
            try {
                LsT8450Response.OutBlock ob = lsMarketClient.getDomesticOrderbook(stockCode);
                onTick(new DomesticQuoteTick(stockCode,
                        toDec(ob.askPrices()), toDec(ob.askVolumes()),
                        toDec(ob.bidPrices()), toDec(ob.bidVolumes())));
            } catch (Exception ex) {
                log.warn("REST 호가 스냅샷 보정 실패 stockCode={} — 다음 틱/재연결 재시도", stockCode, ex);
            }
        }
    }

    /** PENDING 진입(커밋 후) — 인덱스 등록 + 그 종목 첫 PENDING이면 호가 구독 ON. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPendingCreated(PendingOrderCreatedEvent e) {
        if (!DOMESTIC_EXCHANGES.contains(e.exchange())) {
            return;   // 해외(KIS)는 데몬 대상 아님(후속)
        }
        boolean firstForStock = put(e.stockCode(), new Pending(
                e.orderId(), e.userId(), e.accountId(), e.side(), e.limitPrice(), e.quantity(), e.currency()));
        if (firstForStock) {
            subscriptionManager.acquireAsking(e.stockCode());
        }
    }

    /** PENDING 종료(취소 커밋 후) — 인덱스 제거 + 그 종목 PENDING 0건이면 호가 구독 OFF. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPendingClosed(PendingOrderClosedEvent e) {
        if (remove(e.stockCode(), e.orderId())) {
            subscriptionManager.releaseAsking(e.stockCode());
        }
    }

    /**
     * 호가 틱 — 그 종목 PENDING들과 cross 판정, 닿으면 사다리 훑어 전량 가능 시 체결.
     * 단일 LS 세션 스레드에서 순차 호출되므로 종목 내 틱은 직렬. 취소(다른 스레드)와의 경합은
     * {@link PendingFillService}의 조건부 전이가 최종 차단하고, 인덱스 정리는 최선노력(다음 틱 자가복구).
     */
    @EventListener
    public void onTick(DomesticQuoteTick t) {
        Map<Long, Pending> orders = index.get(t.stockCode());
        if (orders == null) {
            return;
        }
        for (Pending p : orders.values()) {   // ConcurrentHashMap weakly-consistent 순회
            if (tryMatch(t, p) && remove(t.stockCode(), p.orderId())) {
                subscriptionManager.releaseAsking(t.stockCode());   // 마지막 PENDING 체결 → 구독 OFF
            }
        }
    }

    /** @return true=인덱스에서 제거 대상(체결됨 또는 이미 종결). false=유지(미도달·잔량부족·일시오류 재시도). */
    private boolean tryMatch(DomesticQuoteTick t, Pending p) {
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
        Map<Long, Pending> m = index.get(stockCode);
        if (m == null) {
            return false;
        }
        m.remove(orderId);
        if (m.isEmpty()) {
            index.remove(stockCode);
            return true;
        }
        return false;
    }

    private Pending toPending(Order o) {
        return new Pending(o.getId(), o.getUserId(), o.getAccountId(), o.getSide(),
                o.getPrice(), o.getOrderQuantity(), o.getCurrency());
    }

    private static BigDecimal first(BigDecimal[] arr) {
        return arr != null && arr.length > 0 && arr[0] != null ? arr[0] : BigDecimal.ZERO;
    }

    /** long[] 호가/잔량 → BigDecimal[](REST 스냅샷 매핑). */
    private static BigDecimal[] toDec(long[] arr) {
        BigDecimal[] out = new BigDecimal[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = BigDecimal.valueOf(arr[i]);
        }
        return out;
    }

    /** 매칭에 필요한 PENDING 주문 스냅샷(돈 이동 권한은 DB 조건부 전이가 최종 검증). */
    private record Pending(Long orderId, Long userId, Long accountId, String side,
                           BigDecimal limitPrice, BigDecimal quantity, String currency) {
    }
}
