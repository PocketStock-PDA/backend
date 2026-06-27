package com.pocketstock.ledger.trading.dto;

import com.pocketstock.ledger.trading.domain.DividendReinvestSetting;

/**
 * 배당 자동 재투자(DRIP) 토글 응답 — 종목별 ON/OFF 상태.
 */
public record DividendReinvestResponse(
        String stockCode,
        String stockName,
        boolean enabled
) {
    public static DividendReinvestResponse from(DividendReinvestSetting s) {
        return new DividendReinvestResponse(
                s.getStockCode(),
                s.getStockName(),
                Boolean.TRUE.equals(s.getIsEnabled())
        );
    }
}
