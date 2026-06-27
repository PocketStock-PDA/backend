package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 웰컴 보상 지급 결과 / 내역 1건.
 * 지급 단위(국내 1,000원 / 해외 $1)를 grantPrice(지급시점 현재가, 종목통화)로 나눠
 * quantity(소수점 수량)를 holdings에 적립한 기록. budgetKrw는 원화 취득원가.
 */
public record WelcomeRewardResponse(
        String stockCode,
        String stockName,
        String market,          // DOMESTIC | OVERSEAS
        String currency,        // KRW | USD
        BigDecimal quantity,    // 지급 소수점 수량
        BigDecimal grantPrice,  // 지급시점 현재가(종목통화)
        int budgetKrw,          // 원화 취득원가(국내 1,000원 / 해외 $1×매매기준율)
        LocalDateTime grantedAt
) {
}
