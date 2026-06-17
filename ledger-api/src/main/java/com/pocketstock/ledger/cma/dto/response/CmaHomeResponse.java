package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CmaHomeResponse(
        Map<String, BigDecimal> cmaBalance,
        BigDecimal interestRate,
        BigDecimal todayInterest,
        BigDecimal collectedToday,
        List<CollectSource> collectSources,
        BigDecimal totalCollectable
) {
    public record CollectSource(
            String sourceType,
            String name,
            BigDecimal amount
    ) {}
}
