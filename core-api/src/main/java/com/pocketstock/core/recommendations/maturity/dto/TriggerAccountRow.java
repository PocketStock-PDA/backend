package com.pocketstock.core.recommendations.maturity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TriggerAccountRow {
    private String accountName;
    private LocalDate maturityDate;
    private BigDecimal maturityAmount;
    private BigDecimal interestRate;
    private int daysUntilMaturity;
}
