package com.pocketstock.core.budget.dto;

import java.util.List;

public record BudgetGoalRequest(List<BudgetGoalItem> categories) {}
