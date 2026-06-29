package com.pocketstock.core.recommendations.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 재예치 원천 예적금 — 같은 상품 재예치 시 상품명·금리·기간 스냅샷용. */
public record RolloverSourceAccount(
        String accountName,
        String accountType,     // DEPOSIT / SAVINGS / ...
        BigDecimal interestRate, // 약정 연이율(소수, 예: 0.0350)
        LocalDate startDate,
        LocalDate maturityDate
) {}
