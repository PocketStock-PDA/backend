package com.pocketstock.ledger.trading.dto;

/**
 * 주문 취소 결과. status는 항상 CANCELLED.
 */
public record OrderCancelResponse(
        Long orderId,
        String status
) {
}
