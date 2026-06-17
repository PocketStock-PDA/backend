package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetGoalSummary(
        BigDecimal monthlyBudget,
        BigDecimal spentAmount,
        BigDecimal remainAmount,
        List<BudgetGoalCategoryItem> categories
) {}
