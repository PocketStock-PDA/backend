package com.pocketstock.ledger.trading.dto;

import com.pocketstock.ledger.trading.domain.AutoInvestTrigger;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 수익률 트리거 1건 응답 (물타기/익절).
 */
public record AutoInvestTriggerResponse(
        Long id,
        String triggerKind,
        BigDecimal conditionRate,
        String actionType,
        BigDecimal actionAmount,
        BigDecimal actionQuantity,
        BigDecimal actionRatio,
        Boolean isActive,
        Boolean isArmed,
        LocalDateTime lastFiredAt
) {
    public static AutoInvestTriggerResponse from(AutoInvestTrigger t) {
        return new AutoInvestTriggerResponse(t.getId(), t.getTriggerKind(), t.getConditionRate(),
                t.getActionType(), t.getActionAmount(), t.getActionQuantity(), t.getActionRatio(),
                t.getIsActive(), t.getIsArmed(), t.getLastFiredAt());
    }
}
