package com.pocketstock.core.recommendations.maturity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TriggerAccountRow {
    private Long accountId;
    private String accountName;
    private LocalDate maturityDate;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private int daysUntilMaturity;
}
