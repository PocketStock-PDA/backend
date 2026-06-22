package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;

/**
 * external_holdings 평면 조회 행(Mapper → Service 내부 전달용).
 * Service에서 증권사(companyCode) 기준으로 묶어 ExternalHoldingResponse로 변환한다.
 */
public record ExternalHoldingRow(
        String companyCode,
        String companyName,
        String stockCode,
        String stockName,
        BigDecimal quantity,
        BigDecimal evaluated
) {}
