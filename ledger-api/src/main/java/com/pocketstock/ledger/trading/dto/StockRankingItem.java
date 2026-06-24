package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 종목 순위 항목 — 브로커 순위(LS/KIS) ∩ 자체 종목마스터(tradable_stocks) 교집합을 재랭킹한 결과.
 * 정렬 기준과 무관하게 거래대금·시가총액을 함께 내려, 프론트에서 탭 전환(거래대금↔시총) 시 재호출이 없게 한다.
 */
public record StockRankingItem(
        int rank,                  // 유니버스 필터 후 1부터 재부여한 순위
        String stockCode,
        String stockName,
        String exchange,
        String currency,
        BigDecimal price,          // 현재가
        BigDecimal changeRate,     // 전일대비 등락율(%)
        BigDecimal tradingValue,   // 거래대금(원)
        BigDecimal marketCap,      // 시가총액(원)
        String logoUrl
) {
}
