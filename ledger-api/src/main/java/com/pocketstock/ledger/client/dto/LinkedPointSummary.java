package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

/**
 * 연동 포인트 요약 — core-api(자산 도메인)에서 Feign으로 읽는 POINT 잔돈 수집 소스(opt-out).
 * 연동된 포인트는 기본 수집 대상이라 collection_settings 행이 없어도 합산하고,
 * 명시적으로 끈(is_enabled=FALSE) 포인트만 제외한다. {@code balance}는 1P=1원.
 */
public record LinkedPointSummary(
        Long id,
        String pointName,
        BigDecimal balance
) {}
