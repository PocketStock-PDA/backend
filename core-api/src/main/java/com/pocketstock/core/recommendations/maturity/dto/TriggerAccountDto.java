package com.pocketstock.core.recommendations.maturity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TriggerAccountDto(
        String accountName,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        int daysUntilMaturity,
        BigDecimal interestRate
) {}
