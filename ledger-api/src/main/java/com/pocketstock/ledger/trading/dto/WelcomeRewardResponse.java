package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 웰컴 보상 지급 결과 / 내역 1건.
 * budgetKrw(예산 1,000원)를 grantPrice(지급시점 현재가, 종목통화)로 환산해
 * quantity(소수점 수량)를 holdings에 적립한 기록.
 */
public record WelcomeRewardResponse(
        String stockCode,
        String stockName,
        String market,          // DOMESTIC | OVERSEAS
        String currency,        // KRW | USD
        BigDecimal quantity,    // 지급 소수점 수량
        BigDecimal grantPrice,  // 지급시점 현재가(종목통화)
        int budgetKrw,          // 지급 예산(원)
        LocalDateTime grantedAt
) {
}
