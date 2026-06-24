package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

/**
 * 외화(USD) 입출금 지갑 요약 — core-api(자산 도메인)에서 Feign으로 읽는 FX 소스.
 * 홈 "수집 가능 잔돈"에 계좌 단위로 그대로 노출하므로 {@code accountName}을 함께 받는다.
 */
public record UsdWalletSummary(
        Long id,
        String accountName,
        BigDecimal balance,
        String currency
) {}
