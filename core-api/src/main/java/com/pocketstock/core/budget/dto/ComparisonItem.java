package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;

public record ComparisonItem(
        String category,
        BigDecimal currentAmount,
        BigDecimal lastAmount,
        BigDecimal changeAmount,
        BigDecimal changeRate
) {}
