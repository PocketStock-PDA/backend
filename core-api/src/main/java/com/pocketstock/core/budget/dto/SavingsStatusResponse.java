package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;

public record SavingsStatusResponse(
        String period,
        BigDecimal totalBudget,
        BigDecimal spentAmount,
        BigDecimal savedAmount,
        BigDecimal targetSavingsAmount,
        boolean isCollectAgreed,
        String transferStatus
) {}
