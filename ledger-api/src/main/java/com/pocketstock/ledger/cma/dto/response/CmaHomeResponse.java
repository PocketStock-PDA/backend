package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CmaHomeResponse(
        Map<String, BigDecimal> cmaBalance,
        BigDecimal interestRate,
        BigDecimal todayInterest,
        List<CollectSource> collectedSources,
        List<CollectSource> collectSources,
        BigDecimal totalCollectable,      // 수집 가능 KRW 소스(ACCOUNT·POINT) 합
        BigDecimal totalCollectableUsd    // 수집 가능 USD 소스(FX) 합 — 통화가 달라 KRW와 분리(b3)
) {
    public record CollectSource(
            String sourceType,
            String name,
            BigDecimal amount,
            String currency   // KRW / USD — 소스별 통화(프론트가 원/달러 표기 분기)
    ) {}
}
