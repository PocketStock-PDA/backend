package com.pocketstock.core.recommendations.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** '예금 재예치' 기록 1건 — 전환내역에서 배당주 예약과 계좌ID로 병합 표시. */
public record DepositRolloverDto(
        Long id,
        Long linkedBankAccountId,
        LocalDate maturityDate,
        String productName,
        String productType,
        long amount,
        BigDecimal baseRate,
        BigDecimal maxRate,
        int periodMonths,
        String status,
        LocalDateTime createdAt
) {}
