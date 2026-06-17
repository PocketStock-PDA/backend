package com.pocketstock.core.budget.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetGoalRow {
    private String category;
    private BigDecimal targetAmount;
}
