package com.pocketstock.core.recommendations.maturity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TriggerAccountDto(
        Long accountId,            // 만기 매수 예약 생성 시 ledger로 전달하는 연동계좌 ID
        String accountName,
        LocalDate maturityDate,
        BigDecimal principalAmount,
        int daysUntilMaturity,
        BigDecimal interestRate,
        boolean reserved           // 이미 자금 굴리기로 예약(활성)한 계좌 — 선택 탭에선 숨기고 전환내역에만 노출
) {}
