package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record CmaHomeResponse(
        Map<String, BigDecimal> cmaBalance,
        CollectableAmount collectableAmount,
        BigDecimal totalCollectable
) {
    public record CollectableAmount(
            BigDecimal account,
            BigDecimal card,
            BigDecimal point
    ) {}
}
