package com.pocketstock.ledger.trading.dto;

/**
 * 종목 상세 — 종목마스터(DB) + 현재가(LS t1102) 합성.
 * ※ price는 국내(KOSPI/KOSDAQ)만 채워지고, 해외(g3101 추후)는 null.
 */
public record StockDetailResponse(
        String stockCode,
        String stockName,
        String englishName,
        String market,
        String standardCode,
        String currency,
        String secType,
        boolean fractional,
        String logoUrl,
        StockPriceResponse price
) {
}
