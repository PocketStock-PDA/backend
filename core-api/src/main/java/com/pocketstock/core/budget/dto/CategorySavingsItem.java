package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;

public record CategorySavingsItem(
        String category,
        BigDecimal targetAmount,
        BigDecimal spentAmount,
        BigDecimal savedAmount,
        BigDecimal usageRate
) {}
