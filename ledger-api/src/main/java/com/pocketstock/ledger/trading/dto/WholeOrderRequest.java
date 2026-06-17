package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 온주 매수/매도 요청. 호가창 기반.
 * - orderType=LIMIT: price(지정가, 호가 탭한 가격) 필수
 * - orderType=MARKET: price 무시, 최우선 호가로 체결
 * - quantity: 정수 주수
 */
public record WholeOrderRequest(
        String stockCode,
        String side,        // BUY | SELL
        String orderType,   // LIMIT | MARKET
        BigDecimal price,   // LIMIT일 때 지정가
        long quantity       // 정수 주수
) {
}
