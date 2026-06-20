package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 웰컴 보상 후보 종목 1건. 국내 거래대금 1·2위 + 해외(NASDAQ) 1·2위에서 추린다.
 * 거래대금 순위에서 종목코드만 받아 종목마스터(tradable_stocks)와 조인해 표시정보를 보강.
 * (섹터는 현재 미보유 — 추후 tradable_stocks 확장 시 추가)
 */
public record WelcomeRewardCandidateResponse(
        String stockCode,
        String stockName,
        String market,          // DOMESTIC | OVERSEAS
        String exchange,        // KOSPI | KOSDAQ | NASDAQ …
        String currency,        // KRW | USD
        BigDecimal tradeAmount, // 거래대금(순위 기준값)
        int rank,               // 거래대금 순위(거래소 기준)
        String logoUrl
) {
}
