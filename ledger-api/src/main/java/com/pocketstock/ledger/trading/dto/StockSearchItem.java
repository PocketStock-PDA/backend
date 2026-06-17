package com.pocketstock.ledger.trading.dto;

/**
 * 종목 검색 결과 항목(자체 종목마스터 기반, LS 호출 없음).
 * 목록 표시에 필요한 최소 필드만 노출 — 현재가는 상세/시세 API에서 합성.
 */
public record StockSearchItem(
        String stockCode,
        String stockName,
        String englishName,
        String exchange,
        String secType,
        String currency,
        String logoUrl
) {
}
