package com.pocketstock.ledger.trading.dto;

import java.util.List;

/**
 * 자동모으기 종합 조회 — 전역 스위치 + 종목별 목록(따로따로 한눈에).
 */
public record AutoInvestOverviewResponse(
        boolean enabled,
        boolean paused,
        boolean keepCollectingOnPause,
        List<AutoInvestResponse> stocks
) {
}
