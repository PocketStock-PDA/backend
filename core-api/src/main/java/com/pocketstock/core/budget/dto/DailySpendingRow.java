package com.pocketstock.core.budget.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DailySpendingRow {
    private String date;
    private BigDecimal spent;
}
