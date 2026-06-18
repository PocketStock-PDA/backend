package com.pocketstock.ledger.exchange.dto.response;

import java.math.BigDecimal;

/**
 * 원화 → 달러 환전 체결 응답(API-exchange.md 스펙).
 * 비용은 적용환율에 내재 — {@code fee=0}. {@code remainKrw}는 체결 후 CMA 원화풀 잔액.
 */
public record KrwToUsdResponse(
        BigDecimal exchangedUsd,   // 환전된 달러(대상 통화)
        BigDecimal appliedRate,    // 적용환율(매수, 체결 박제)
        BigDecimal fee,            // 0 (스프레드 내재)
        String triggerType,        // MANUAL
        BigDecimal remainKrw       // 체결 후 CMA 원화풀 잔액
) {
}
