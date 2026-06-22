package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 타사 보유 소수점(타 증권사) 통합 조회 — 증권사 단위로 묶은 응답.
 * 평가금액(evaluated)은 시드 기준 정적 값(타사 보유는 목데이터, 실시간 시세 미연동).
 */
public record ExternalHoldingResponse(
        String companyCode,
        String companyName,
        List<Holding> stocks
) {
    public record Holding(
            String stockCode,
            String stockName,
            BigDecimal quantity,
            BigDecimal evaluated
    ) {}
}
