package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 소수점 접수 결과 — 비동기 전제라 즉시 {@code QUEUED} 반환(체결 아님).
 * estQuantity는 접수 시점 예상 체결수량(참고치), 확정 체결가·수량은 차수 집행 후 allocations에서.
 * heldAmount = 실제 잠근 금액(D1). orderable = 접수 후 주문가능 예수금(매수, KRW/USD) 또는 null(매도).
 */
public record FractionalOrderResponse(
        Long orderId,
        Long roundId,
        String stockCode,
        String side,          // BUY | SELL
        String orderType,     // AMOUNT | QUANTITY | ALL
        BigDecimal estQuantity,
        BigDecimal heldAmount,
        String status,        // QUEUED
        BigDecimal orderable
) {
}
