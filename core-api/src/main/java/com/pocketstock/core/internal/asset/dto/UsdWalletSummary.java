package com.pocketstock.core.internal.asset.dto;

import java.math.BigDecimal;

/**
 * 외화(USD) 입출금 지갑 요약 — 잔돈 수집의 FX 소스(전액 입금 대상).
 * 홈 "수집 가능 잔돈"에 계좌 단위로 그대로 노출하므로 {@code accountName}을 함께 반환한다
 * (여러 지갑을 한 라벨로 합치지 않는다).
 */
public record UsdWalletSummary(
        Long id,
        String accountName,
        BigDecimal balance,
        String currency
) {}
