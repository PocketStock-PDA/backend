package com.pocketstock.core.internal.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LinkedAccountSummary(
        Long id,
        String accountType,
        BigDecimal balance,
        String currency,
        LocalDate maturityDate   // 예적금(DEPOSIT/SAVINGS) 만기일 — 만기 매수 예약 트리거용. 그 외 NULL
) {}
