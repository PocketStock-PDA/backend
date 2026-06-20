package com.pocketstock.core.budget.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategorySavingsRow {
    private String category;
    private BigDecimal targetAmount;
    private BigDecimal spentAmount;
}
