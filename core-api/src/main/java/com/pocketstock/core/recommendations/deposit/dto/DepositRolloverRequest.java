package com.pocketstock.core.recommendations.deposit.dto;

/** '예금 재예치' 생성 요청 — 만기된 예적금과 같은 상품으로 재예치(상품은 서버가 계좌에서 도출). */
public record DepositRolloverRequest(
        Long linkedBankAccountId,
        Long amount
) {}
