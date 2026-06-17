package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record AutoBudgetGoalResponse(BigDecimal monthlyBudget, List<BudgetGoalItem> categories) {}
