package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 자동모으기 수익률 트리거 (auto_invest_triggers, DB B) — 물타기(BUY)·익절(SELL), 옵션 레이어.
 * 종목당 종류별 1행({@code (auto_invest_stock_id, trigger_kind)} UNIQUE). 평가 = 일배치 종가 수익률(#125).
 * 재발동 = 에지({@code is_armed}): 발동→false, 수익률이 조건 밖으로 나가면→true. armed&&조건충족일 때만 실행.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoInvestTrigger {

    private Long id;
    private Long autoInvestStockId;
    private String triggerKind;       // BUY(물타기) / SELL(익절)
    private BigDecimal conditionRate; // BUY: 수익률 ≤ (예 -7.0) · SELL: 수익률 ≥ (예 +15.0)
    private String actionType;        // BUY: AMOUNT/QUANTITY · SELL: RATIO/QUANTITY/ALL
    private BigDecimal actionAmount;  // BUY 추가매수 금액 (AMOUNT)
    private BigDecimal actionQuantity; // BUY/SELL 수량 (QUANTITY)
    private BigDecimal actionRatio;   // SELL 보유 비율% (RATIO)
    private Boolean isActive;
    private Boolean isArmed;
    private LocalDateTime lastFiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 평가 join 전용(테이블 컬럼 아님) — auto_invest_stocks 조인 ----
    private Long userId;
    private String stockCode;
    private String market;
    private Long accountId;
    private String currency;
}
