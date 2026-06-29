package com.pocketstock.ledger.client.dto;

/** core의 만기 도래 'CMA 이체' 예약 1건 — 만기 스케줄러가 계좌→CMA 집행할 대상. */
public record DueCmaTransferView(
        Long id,
        Long userId,
        Long linkedBankAccountId,
        java.math.BigDecimal amount
) {}
