package com.pocketstock.ledger.trading.matching;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.ledger.config.RealtimeSubscriptionManager;
import com.pocketstock.ledger.trading.domain.AutoInvestTrigger;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.mapper.AutoInvestTriggerMapper;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.realtime.OrderNotification;
import com.pocketstock.ledger.trading.realtime.OrderNotificationPublisher;
import com.pocketstock.ledger.trading.service.AutoInvestExecutionRecorder;
import com.pocketstock.ledger.trading.service.FractionalOrderService;
import com.pocketstock.ledger.trading.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 수익률 트리거 실시간 감지 엔진(#194) — 온주 매칭엔진({@link WholeOrderMatchingEngine})과 동형.
 * 호가 틱({@link QuoteTick})을 받아 종목별 활성 트리거와 cross 판정, 닿으면 물타기/익절 발동.
 *
 * <p>"현재가"는 호가에서 유도 — 물타기(살 거)=최우선 매도호가(ask[0], 지금 사는 값) / 익절(팔 거)=최우선 매수호가(bid[0]).
 * 매칭엔진이 같은 이유로 호가를 쓴다(체결가=과거 흔적, 호가=지금 거래 가능한 진짜 가격). 수익률 = (현재가 − 평단)/평단.
 *
 * <p>구독 = 트리거 생명주기(온디맨드): 종목 첫 트리거면 호가 구독 ON, 마지막 해제면 OFF. 가격을 계속 봐야 하므로
 * PENDING(체결되면 끝)과 달리 트리거가 걸려있는 동안 계속 ON. 인덱스 SSOT=DB(auto_invest_triggers) — 부팅 시 재적재.
 * 재발동 = 에지({@code is_armed}): 발동 시 armed=false, 조건 밖으로 나가면 armed=true. armed&&조건충족일 때만 실행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoInvestTriggerEngine {

    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String SOURCE_AUTO = "AUTO";
    private static final String KIND_BUY = "BUY";
    private static final String KIND_SELL = "SELL";
    private static final String SRC_DIP_BUY = "DIP_BUY";
    private static final String SRC_TAKE_PROFIT = "TAKE_PROFIT";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int QTY_SCALE = 6;

    private final AutoInvestTriggerMapper triggerMapper;
    private final HoldingMapper holdingMapper;
    private final StockMapper stockMapper;
    private final RealtimeSubscriptionManager subscriptionManager;
    private final FractionalOrderService fractionalOrderService;
    private final AutoInvestExecutionRecorder recorder;
    private final OrderNotificationPublisher orderNotificationPublisher;
    private final StockPriceService stockPriceService;

    /** stockCode → (triggerId → 트리거 스냅샷). 평가용 캐시(SSOT=DB). */
    private final Map<String, Map<Long, Trig>> index = new ConcurrentHashMap<>();
    /** stockCode → exchange (구독 ON/OFF 분기용 — 트리거엔 market만 있어 마스터에서 1회 파생). */
    private final Map<String, String> exchangeOf = new ConcurrentHashMap<>();

    /** 부팅 복구 — DB 활성 트리거 전건을 인덱스에 재적재하고 종목별 호가 구독을 켠다. */
    @EventListener(ApplicationReadyEvent.class)
    public void reload() {
        int n = 0;
        for (String market : List.of("DOMESTIC", "OVERSEAS")) {
            for (AutoInvestTrigger t : triggerMapper.findActiveByMarket(market)) {
                put(t);
                n++;
            }
        }
        index.keySet().forEach(this::acquireQuote);
        log.info("수익률 트리거 실시간 — 활성 {}건 재적재, 종목 {}개 호가 구독 ON", n, index.size());
    }

    /** 트리거 등록/활성(커밋 후) — 인덱스 등록 + 그 종목 첫 트리거면 구독 ON. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArmed(TriggerArmedEvent e) {
        AutoInvestTrigger t = reloadTrigger(e.triggerId(), e.stockCode());
        if (t == null) {
            return;   // 비활성/삭제됨
        }
        if (put(t)) {
            acquireQuote(e.stockCode());
        }
    }

    /** 트리거 해제(커밋 후) — 인덱스 제거 + 그 종목 트리거 0건이면 구독 OFF. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisarmed(TriggerDisarmedEvent e) {
        if (remove(e.stockCode(), e.triggerId())) {
            releaseQuote(e.stockCode());
        }
    }

    /**
     * 호가 틱 — 그 종목 트리거들과 cross 판정. 물타기=ask[0]·익절=bid[0]로 현재가 잡아 수익률 계산.
     * 매칭엔진과 같은 틱({@link QuoteTick})을 공유한다(별도 시세 구독 안 만듦).
     */
    @EventListener
    public void onTick(QuoteTick t) {
        Map<Long, Trig> trigs = index.get(t.stockCode());
        if (trigs == null) {
            return;
        }
        BigDecimal ask = first(t.askPrices());   // 살 때 현재가
        BigDecimal bid = first(t.bidPrices());   // 팔 때 현재가
        for (Trig tr : trigs.values()) {
            try {
                evaluate(tr, ask, bid);
            } catch (Exception e) {
                log.error("[트리거] 평가 실패 triggerId={} stock={}", tr.triggerId(), t.stockCode(), e);
            }
        }
    }

    /** 한 트리거 평가 — 현재가로 수익률 계산 → cross & armed면 발동 / 조건 밖 & 미armed면 재무장(에지). */
    private void evaluate(Trig tr, BigDecimal ask, BigDecimal bid) {
        boolean buy = KIND_BUY.equals(tr.kind());
        BigDecimal price = buy ? ask : bid;        // 물타기=살 값(ask)·익절=팔 값(bid)
        if (price.signum() <= 0) {
            return;   // 해당 방향 호가 없음
        }
        BigDecimal avg = holdingMapper.findByUserIdAndStock(tr.userId(), tr.stockCode()) == null
                ? null
                : holdingMapper.findByUserIdAndStock(tr.userId(), tr.stockCode()).getAvgBuyPrice();
        if (avg == null || avg.signum() <= 0) {
            return;   // 보유/평단 없음(무상주 등) — 수익률 산정 불가
        }
        BigDecimal rate = price.subtract(avg).divide(avg, 6, RoundingMode.HALF_UP).multiply(HUNDRED);
        boolean conditionMet = buy
                ? rate.compareTo(tr.conditionRate()) <= 0   // 물타기: 수익률 ≤ -X
                : rate.compareTo(tr.conditionRate()) >= 0;  // 익절: 수익률 ≥ +X

        if (conditionMet && tr.armed()) {
            fire(tr);
        } else if (!conditionMet && !tr.armed()) {
            triggerMapper.rearm(tr.triggerId());          // 조건 밖으로 나감 → 재무장
            trigs(tr.stockCode()).computeIfPresent(tr.triggerId(), (id, x) -> x.withArmed(true));
        }
    }

    /** 발동 — 물타기 place / 익절 placeSell(기존 엔진 재사용). 회차 로그 + WS 통보. armed 소진. */
    private void fire(Trig tr) {
        boolean buy = KIND_BUY.equals(tr.kind());
        LocalDate today = LocalDate.now(KST);
        String clientOrderId = "AUTOTRG_" + tr.triggerId() + "_" + LocalDateTime.now(KST).format(ORDER_DATE);
        String source = buy ? SRC_DIP_BUY : SRC_TAKE_PROFIT;
        String side = buy ? KIND_BUY : KIND_SELL;
        int roundNo = recorder.nextRoundNo(tr.stockId());
        try {
            SplitOrderResponse resp = buy ? fireBuy(tr, clientOrderId) : fireSell(tr, clientOrderId);
            if (resp == null) {
                recorder.recordFailed(tr.userId(), tr.stockId(), tr.stockCode(), roundNo, today, source, side,
                        tr.currency(), "매도 가능 수량 없음");
                return;   // armed 유지(다음 틱 재시도)
            }
            triggerMapper.markFired(tr.triggerId(), LocalDateTime.now());   // 발동 → armed=false
            trigs(tr.stockCode()).computeIfPresent(tr.triggerId(), (id, x) -> x.withArmed(false));
            recorder.recordAccepted(tr.userId(), tr.stockId(), tr.stockCode(), roundNo, today, source, side,
                    tr.currency(), resp);
            // 실시간 트리거 통보(#139 WS) — 보고 있을 때 즉시.
            Long orderId = resp.fractionalOrderId() != null ? resp.fractionalOrderId() : resp.wholeOrderId();
            orderNotificationPublisher.push(tr.userId(), new OrderNotification(orderId, tr.stockCode(), side,
                    "FRACTIONAL", "ACCEPTED", null, null, tr.currency(), LocalDateTime.now(KST).toString()));
        } catch (BusinessException e) {
            recorder.recordFailed(tr.userId(), tr.stockId(), tr.stockCode(), roundNo, today, source, side,
                    tr.currency(), e.getMessage());   // armed 유지
        }
    }

    private SplitOrderResponse fireBuy(Trig tr, String clientOrderId) {
        FractionalOrderRequest req = new FractionalOrderRequest(clientOrderId, tr.stockCode(),
                "BUY", tr.actionType(), tr.actionAmount(), tr.actionQuantity());
        return fractionalOrderService.place(tr.userId(), req, SOURCE_AUTO);
    }

    /** 익절 — RATIO는 보유×%, 수량은 설정값. 매도가능으로 클램프. ALL은 전량. 매도가능 0이면 null. */
    private SplitOrderResponse fireSell(Trig tr, String clientOrderId) {
        Holding h = holdingMapper.findByUserIdAndStock(tr.userId(), tr.stockCode());
        BigDecimal quantity = (h == null || h.getQuantity() == null) ? BigDecimal.ZERO : h.getQuantity();
        BigDecimal sellable = quantity
                .subtract(nz(h == null ? null : h.getHeldWhole()))
                .subtract(nz(h == null ? null : h.getHeldFractional()));
        if (sellable.signum() <= 0) {
            return null;
        }
        if ("ALL".equals(tr.actionType())) {
            return fractionalOrderService.placeSell(tr.userId(),
                    new FractionalOrderRequest(clientOrderId, tr.stockCode(), "SELL", "ALL", null, null), SOURCE_AUTO);
        }
        BigDecimal requested = "RATIO".equals(tr.actionType())
                ? quantity.multiply(tr.actionRatio()).divide(HUNDRED, QTY_SCALE, RoundingMode.DOWN)
                : tr.actionQuantity();
        BigDecimal qty = requested.min(sellable).setScale(QTY_SCALE, RoundingMode.DOWN);
        if (qty.signum() <= 0) {
            return null;
        }
        return fractionalOrderService.placeSell(tr.userId(),
                new FractionalOrderRequest(clientOrderId, tr.stockCode(), "SELL", "QUANTITY", null, qty), SOURCE_AUTO);
    }

    /**
     * dev 강제 평가(#194) — 실시간 틱은 장중에만 오므로, 테스트용으로 그 종목 현재가(시세 캐시/REST)를
     * ask[0]=bid[0]로 한 가짜 틱을 만들어 즉시 평가한다. 운영 경로 아님(DevController 전용).
     */
    public int devEvaluate(String stockCode) {
        Map<Long, Trig> trigs = index.get(stockCode);
        if (trigs == null || trigs.isEmpty()) {
            return 0;
        }
        Long userId = trigs.values().iterator().next().userId();
        var p = isOverseas(stockCode)
                ? stockPriceService.getOverseasPrice(userId, stockCode)
                : stockPriceService.getDomesticPrice(userId, stockCode);
        BigDecimal price = p == null ? BigDecimal.ZERO : p.currentPrice();
        BigDecimal[] one = {price};
        BigDecimal[] vol = {BigDecimal.ONE};
        onTick(new DomesticQuoteTick(stockCode, one, vol, one, vol));   // ask[0]=bid[0]=현재가
        return trigs.size();
    }

    // ---- 구독 dispatch (매칭엔진과 동일: 국내 LS UH1 / 해외 KIS HDFSASP0) ----

    private void acquireQuote(String stockCode) {
        if (isOverseas(stockCode)) {
            subscriptionManager.acquireForeignQuote(stockCode);
        } else {
            subscriptionManager.acquireAsking(stockCode);
        }
    }

    private void releaseQuote(String stockCode) {
        if (isOverseas(stockCode)) {
            subscriptionManager.releaseForeignQuote(stockCode);
        } else {
            subscriptionManager.releaseAsking(stockCode);
        }
        exchangeOf.remove(stockCode);
    }

    private boolean isOverseas(String stockCode) {
        String ex = exchangeOf.computeIfAbsent(stockCode, code -> {
            TradableStock s = stockMapper.findByCode(code);
            return s == null ? "" : s.getExchange();
        });
        return OVERSEAS_EXCHANGES.contains(ex);
    }

    // ---- 인덱스 ----

    private Map<Long, Trig> trigs(String stockCode) {
        return index.getOrDefault(stockCode, new ConcurrentHashMap<>());
    }

    /** @return 그 종목 첫 트리거면 true(구독 ON). */
    private boolean put(AutoInvestTrigger t) {
        boolean[] first = {false};
        Map<Long, Trig> m = index.computeIfAbsent(t.getStockCode(), k -> {
            first[0] = true;
            return new ConcurrentHashMap<>();
        });
        m.put(t.getId(), Trig.of(t));
        return first[0];
    }

    /** @return 제거로 그 종목 트리거 0건이 되면 true(구독 OFF). */
    private boolean remove(String stockCode, Long triggerId) {
        boolean[] becameEmpty = {false};
        index.computeIfPresent(stockCode, (code, m) -> {
            m.remove(triggerId);
            if (m.isEmpty()) {
                becameEmpty[0] = true;
                return null;
            }
            return m;
        });
        return becameEmpty[0];
    }

    /** DB에서 트리거 1건 재조회(등록/수정 후 최신 스냅샷). 비활성/없으면 null. */
    private AutoInvestTrigger reloadTrigger(Long triggerId, String stockCode) {
        for (AutoInvestTrigger t : triggerMapper.findActiveByMarket(domesticOrOverseas(stockCode))) {
            if (t.getId().equals(triggerId)) {
                return t;
            }
        }
        return null;
    }

    private String domesticOrOverseas(String stockCode) {
        return isOverseas(stockCode) ? "OVERSEAS" : "DOMESTIC";
    }

    private static BigDecimal first(BigDecimal[] arr) {
        return arr != null && arr.length > 0 && arr[0] != null ? arr[0] : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 평가용 트리거 스냅샷(돈 이동 권한은 place/placeSell의 DB 가드가 최종 검증). armed는 인덱스에서 관리. */
    private record Trig(Long triggerId, Long stockId, Long userId, String stockCode, String currency,
                        String kind, BigDecimal conditionRate, String actionType,
                        BigDecimal actionAmount, BigDecimal actionQuantity, BigDecimal actionRatio, boolean armed) {
        static Trig of(AutoInvestTrigger t) {
            return new Trig(t.getId(), t.getAutoInvestStockId(), t.getUserId(), t.getStockCode(), t.getCurrency(),
                    t.getTriggerKind(), t.getConditionRate(), t.getActionType(),
                    t.getActionAmount(), t.getActionQuantity(), t.getActionRatio(),
                    Boolean.TRUE.equals(t.getIsArmed()));
        }

        Trig withArmed(boolean a) {
            return new Trig(triggerId, stockId, userId, stockCode, currency, kind, conditionRate, actionType,
                    actionAmount, actionQuantity, actionRatio, a);
        }
    }
}
