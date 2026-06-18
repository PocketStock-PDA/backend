package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CmaBalanceResponse(
        List<BalanceItem> accounts,
        BigDecimal totalKrwEquivalent
) {
    public record BalanceItem(
            String currency,
            BigDecimal balance,
            BigDecimal interestRate,
            String type
    ) {}
}
