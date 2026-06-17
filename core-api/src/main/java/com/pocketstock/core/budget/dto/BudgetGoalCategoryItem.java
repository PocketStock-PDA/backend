package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;

public record BudgetGoalCategoryItem(String category, BigDecimal budget, BigDecimal spent) {}
