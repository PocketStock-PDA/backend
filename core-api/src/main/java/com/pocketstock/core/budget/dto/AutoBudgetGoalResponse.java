package com.pocketstock.core.budget.dto;

import java.util.List;

public record AutoBudgetGoalResponse(Long monthlyBudget, List<BudgetGoalItem> categories) {}
