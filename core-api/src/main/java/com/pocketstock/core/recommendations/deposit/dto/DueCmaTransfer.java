package com.pocketstock.core.recommendations.deposit.dto;

/** 만기 도래한 'CMA 이체' 예약 1건 — ledger 스케줄러가 만기일에 계좌→CMA 집행할 대상. */
public record DueCmaTransfer(
        Long id,
        Long userId,
        Long linkedBankAccountId,
        Long amount
) {}
