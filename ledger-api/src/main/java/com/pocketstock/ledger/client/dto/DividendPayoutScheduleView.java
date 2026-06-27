package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

/**
 * 지급일 배당 일정 1행(core-api) — (종목, 주당배당금). 배당 지급 엔진이 보유자와 조인해 지급.
 */
public record DividendPayoutScheduleView(
        String stockCode,
        BigDecimal perShare   // 주당 현금배당금(KRW)
) {}
