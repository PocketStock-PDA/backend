package com.pocketstock.core.budget.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetSavingsRow {
    private BigDecimal targetAmount;
    private boolean collectAgreed;
    private String transferStatus;
}
