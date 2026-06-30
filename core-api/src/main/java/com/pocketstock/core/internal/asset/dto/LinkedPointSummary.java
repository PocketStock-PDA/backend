package com.pocketstock.core.internal.asset.dto;

import java.math.BigDecimal;

/**
 * 연동 포인트 요약 — 잔돈 수집의 POINT 소스(opt-out: 연동된 포인트는 기본 수집 대상).
 * 홈 "수집 가능 잔돈"에 신한/제휴로 분류해 노출하므로 {@code pointName}을 함께 반환한다.
 * {@code balance}는 1P=1원(linked_points.balance, BIGINT).
 */
public record LinkedPointSummary(
        Long id,
        String pointName,
        BigDecimal balance
) {}
