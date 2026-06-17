package com.pocketstock.core.internal.asset.dto;

import java.math.BigDecimal;

public record LinkedAccountSummary(
        Long id,
        String accountType,
        BigDecimal balance,
        String currency
) {}
