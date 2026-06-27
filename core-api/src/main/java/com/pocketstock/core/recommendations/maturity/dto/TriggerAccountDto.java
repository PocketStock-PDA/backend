package com.pocketstock.core.recommendations.maturity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TriggerAccountDto(
        Long accountId,            // 만기 매수 예약 생성 시 ledger로 전달하는 연동계좌 ID
        String accountName,
        LocalDate maturityDate,
        BigDecimal principalAmount,
        int daysUntilMaturity,
        BigDecimal interestRate
) {}
