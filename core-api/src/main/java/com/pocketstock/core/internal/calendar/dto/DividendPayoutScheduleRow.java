package com.pocketstock.core.internal.calendar.dto;

import java.math.BigDecimal;

/**
 * 지급일 배당 일정 1행 — 배당 지급 엔진(ledger)이 보유자와 조인할 (종목, 주당배당금).
 */
public record DividendPayoutScheduleRow(
        String stockCode,
        BigDecimal perShare   // 주당 현금배당금(KRW)
) {}
