package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

public record LinkedAccountSummary(
        Long id,
        String accountType,
        BigDecimal balance,
        String currency
) {}
