package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 온주 주문 체결 결과. 자체 시뮬 즉시 전량 체결(status=FILLED).
 * totalAmount = fillPrice × quantity (수수료·세금 미반영, MVP).
 * balanceAfter = 체결 후 예수금 잔액(KRW).
 */
public record WholeOrderResponse(
        Long orderId,
        String stockCode,
        String side,
        long quantity,
        BigDecimal fillPrice,
        BigDecimal totalAmount,
        String status,
        BigDecimal balanceAfter
) {
}
