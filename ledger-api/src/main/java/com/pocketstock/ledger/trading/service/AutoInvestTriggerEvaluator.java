package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.ledger.trading.domain.AutoInvestTrigger;
import com.pocketstock.ledger.trading.domain.DailyValuation;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.mapper.AutoInvestTriggerMapper;
import com.pocketstock.ledger.trading.mapper.DailyValuationMapper;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 자동모으기 수익률 트리거 평가기(#194) — 일배치 종가 수익률(daily_valuations #125)로 물타기/익절을 발동한다.
 * 정기매수 스케줄러(국내 09:10·해외 22:40)가 정기매수 직후 호출 — 같은 시각 종가 수익률로 평가.
 *
 * <p>재발동 = 에지({@code is_armed}): 발동 시 armed=false, 수익률이 조건 밖으로 나가면 armed=true → armed&&조건충족일 때만 실행.
 * 신규 체결엔진 없음 — 물타기=place(AUTO)·익절=placeSell(AUTO)로 기존 소수점 엔진 재사용. 회차 로그는 정기매수와 공용 recorder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoInvestTriggerEvaluator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SOURCE_AUTO = "AUTO";
    private static final String KIND_BUY = "BUY";
    private static final String KIND_SELL = "SELL";
    private static final String SRC_DIP_BUY = "DIP_BUY";
    private static final String SRC_TAKE_PROFIT = "TAKE_PROFIT";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int QTY_SCALE = 6;

    private final AutoInvestTriggerMapper triggerMapper;
    private final DailyValuationMapper dailyValuationMapper;
    private final HoldingMapper holdingMapper;
    private final FractionalOrderService fractionalOrderService;
    private final AutoInvestExecutionRecorder recorder;

    /** 시장별 트리거 평가 — 종목 단위 격리(한 트리거 실패가 전체 멈추지 않게). */
    public void evaluate(String market) {
        LocalDate today = LocalDate.now(KST);
        List<AutoInvestTrigger> triggers = triggerMapper.findActiveByMarket(market);
        log.info("[자동모으기] {} 트리거 평가 — 대상 {}건 ({})", market, triggers.size(), today);
        for (AutoInvestTrigger t : triggers) {
            try {
                evaluateOne(t, today);
            } catch (Exception e) {
                log.error("[자동모으기] 트리거 평가 실패 triggerId={} stock={}", t.getId(), t.getStockCode(), e);
            }
        }
    }

    private void evaluateOne(AutoInvestTrigger t, LocalDate today) {
        DailyValuation dv = dailyValuationMapper.findLatestByUserAndStock(t.getUserId(), t.getStockCode());
        if (dv == null || dv.getProfitRate() == null) {
            return;   // 평가 스냅샷(#125) 없음 — 판정 불가, 스킵
        }
        BigDecimal rate = dv.getProfitRate();
        boolean buy = KIND_BUY.equals(t.getTriggerKind());
        boolean conditionMet = buy
                ? rate.compareTo(t.getConditionRate()) <= 0   // 물타기: 수익률 ≤ -X
                : rate.compareTo(t.getConditionRate()) >= 0;  // 익절: 수익률 ≥ +X
        boolean armed = Boolean.TRUE.equals(t.getIsArmed());

        if (conditionMet && armed) {
            fire(t, today, buy);
        } else if (!conditionMet && !armed) {
            triggerMapper.rearm(t.getId());   // 조건 밖으로 나감 → 재무장(에지)
        }
        // conditionMet && !armed: 발동 후 대기 / !conditionMet && armed: 그대로
    }

    private void fire(AutoInvestTrigger t, LocalDate today, boolean buy) {
        String clientOrderId = "AUTOTRG_" + t.getId() + "_" + today.format(ORDER_DATE);
        String source = buy ? SRC_DIP_BUY : SRC_TAKE_PROFIT;
        String side = buy ? KIND_BUY : KIND_SELL;
        int roundNo = recorder.nextRoundNo(t.getAutoInvestStockId());
        try {
            SplitOrderResponse resp = buy ? fireBuy(t, clientOrderId) : fireSell(t, clientOrderId);
            if (resp == null) {
                // 매도가능 0 등 — 발동 못 함. is_armed 유지(다음 평가 재시도), FAILED 기록.
                recorder.recordFailed(t.getAutoInvestStockId(), roundNo, today, source, side, t.getCurrency(), "매도 가능 수량 없음");
                return;
            }
            triggerMapper.markFired(t.getId(), LocalDateTime.now());   // 발동 → 에지 소진(armed=false)
            recorder.recordAccepted(t.getAutoInvestStockId(), roundNo, today, source, side, t.getCurrency(), resp);
        } catch (BusinessException e) {
            // 잔액부족 등 — markFired 안 함(armed 유지 → 다음 평가 재시도). FAILED 기록.
            recorder.recordFailed(t.getAutoInvestStockId(), roundNo, today, source, side, t.getCurrency(), e.getMessage());
        }
    }

    /** 물타기 — 기존 소수점 매수 엔진 재사용(정수=온주 직결·끝수=차수). 금액/수량은 트리거 설정값. */
    private SplitOrderResponse fireBuy(AutoInvestTrigger t, String clientOrderId) {
        FractionalOrderRequest req = new FractionalOrderRequest(clientOrderId, t.getStockCode(),
                "BUY", t.getActionType(), t.getActionAmount(), t.getActionQuantity());
        return fractionalOrderService.place(t.getUserId(), req, SOURCE_AUTO);
    }

    /**
     * 익절 매도 — RATIO는 발동 시점 보유수량×%로 환산, 수량은 설정값. 모두 매도가능으로 클램프(부족시 가능한 만큼).
     * ALL은 전량(placeSell이 매도가능 전량 처리). 매도가능 0이면 null(발동 스킵).
     */
    private SplitOrderResponse fireSell(AutoInvestTrigger t, String clientOrderId) {
        Holding h = holdingMapper.findByUserIdAndStock(t.getUserId(), t.getStockCode());
        BigDecimal quantity = (h == null || h.getQuantity() == null) ? BigDecimal.ZERO : h.getQuantity();
        BigDecimal sellable = quantity
                .subtract(nz(h == null ? null : h.getHeldWhole()))
                .subtract(nz(h == null ? null : h.getHeldFractional()));
        if (sellable.signum() <= 0) {
            return null;
        }
        String action = t.getActionType();
        if ("ALL".equals(action)) {
            FractionalOrderRequest req = new FractionalOrderRequest(clientOrderId, t.getStockCode(),
                    "SELL", "ALL", null, null);
            return fractionalOrderService.placeSell(t.getUserId(), req, SOURCE_AUTO);
        }
        BigDecimal requested = "RATIO".equals(action)
                ? quantity.multiply(t.getActionRatio()).divide(HUNDRED, QTY_SCALE, RoundingMode.DOWN)   // 보유×%
                : t.getActionQuantity();
        BigDecimal qty = requested.min(sellable).setScale(QTY_SCALE, RoundingMode.DOWN);   // 부족시 가능한 만큼 클램프
        if (qty.signum() <= 0) {
            return null;
        }
        FractionalOrderRequest req = new FractionalOrderRequest(clientOrderId, t.getStockCode(),
                "SELL", "QUANTITY", null, qty);
        return fractionalOrderService.placeSell(t.getUserId(), req, SOURCE_AUTO);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
