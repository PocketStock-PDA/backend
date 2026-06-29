package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LinkedAccountSummary(
        Long id,
        String accountType,
        BigDecimal balance,
        String currency,
        LocalDate maturityDate,  // 예적금(DEPOSIT/SAVINGS) 만기일 — 만기 매수 예약 트리거용. 그 외 NULL
        BigDecimal maturityAmount // 총 수령액(원금+만기이자) — 만기 굴리기 집행 한도. 비예적금은 balance와 동일
) {}
