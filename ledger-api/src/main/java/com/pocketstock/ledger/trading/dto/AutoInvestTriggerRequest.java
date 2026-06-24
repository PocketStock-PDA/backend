package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 수익률 트리거 등록/수정 요청 (물타기/익절).
 * - triggerKind=BUY(물타기): conditionRate=수익률 ≤(예 -7.0) · actionType=AMOUNT/QUANTITY
 * - triggerKind=SELL(익절): conditionRate=수익률 ≥(예 +15.0) · actionType=RATIO/QUANTITY/ALL
 */
public record AutoInvestTriggerRequest(
        String triggerKind,        // BUY | SELL
        BigDecimal conditionRate,
        String actionType,         // AMOUNT | QUANTITY | RATIO | ALL
        BigDecimal actionAmount,   // BUY AMOUNT
        BigDecimal actionQuantity, // BUY/SELL QUANTITY
        BigDecimal actionRatio     // SELL RATIO(%)
) {
}
