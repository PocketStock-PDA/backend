package com.pocketstock.ledger.trading.realtime;

import java.math.BigDecimal;

/**
 * 실시간 체결통보 payload(#139) — {@code /topic/order-notification/{userId}} 로 push.
 * 같은 비동기 체결 이벤트의 WS 출구(앱 켜져 있을 때 즉시 화면 반영). 영속 알림은 Kafka→core(#204)가 별도 담당.
 */
public record OrderNotification(
        Long orderId,
        String stockCode,
        String side,         // BUY | SELL
        String orderType,    // FRACTIONAL | LIMIT
        String status,       // FILLED | REJECTED
        BigDecimal filledQuantity,
        BigDecimal filledPrice,
        String currency,
        String filledAt      // ISO-8601 (KST)
) {
}
