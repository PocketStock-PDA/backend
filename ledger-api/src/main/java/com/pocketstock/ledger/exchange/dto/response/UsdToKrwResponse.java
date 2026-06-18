package com.pocketstock.ledger.exchange.dto.response;

import java.math.BigDecimal;

/**
 * 달러 → 원화 환전 체결 응답(API-exchange.md 스펙).
 * 비용은 적용환율에 내재 — {@code fee=0}. {@code remainUsd}는 체결 후 CMA 달러풀 잔액.
 */
public record UsdToKrwResponse(
        BigDecimal exchangedKrw,   // 환전된 원화(대상 통화)
        BigDecimal appliedRate,    // 적용환율(매도, 체결 박제)
        BigDecimal fee,            // 0 (스프레드 내재)
        String triggerType,        // MANUAL
        BigDecimal remainUsd       // 체결 후 CMA 달러풀 잔액
) {
}
