package com.pocketstock.core.recommendations.deposit.dto;

import java.math.BigDecimal;

/** 예적금 상품 카탈로그 1건 — '예금 재예치' 추천 목록용. */
public record DepositProductDto(
        Long id,
        String productName,
        String productType,   // 정기예금 / 정기적금
        int periodMonths,
        BigDecimal baseRate,  // 기본금리(%)
        BigDecimal maxRate,   // 최고금리(우대 포함, %)
        long minAmount,
        Long maxAmount        // 한도 없으면 null
) {}
