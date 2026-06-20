package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record CategorySavingsResponse(
        String period,
        BigDecimal totalBudget,
        BigDecimal totalSpent,
        BigDecimal totalSaved,
        List<CategorySavingsItem> categories
) {}
