package com.pocketstock.ledger.trading.dto;

/**
 * 배당 자동 재투자(DRIP) 토글 설정 요청 — 종목별 ON/OFF.
 *
 * @param stockCode 대상 배당주(보유 중인 국내 종목)
 * @param enabled   true=재투자 ON / false=현금 수령(CMA 잔류)
 */
public record DividendReinvestRequest(
        String stockCode,
        Boolean enabled
) {
}
