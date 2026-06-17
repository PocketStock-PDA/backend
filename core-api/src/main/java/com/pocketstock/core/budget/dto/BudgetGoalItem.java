package com.pocketstock.core.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BudgetGoalItem(
        @NotBlank String category,
        @NotNull @Positive BigDecimal budget
) {}
