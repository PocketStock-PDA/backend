package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;

public record BudgetGoalItem(String category, BigDecimal budget) {}
