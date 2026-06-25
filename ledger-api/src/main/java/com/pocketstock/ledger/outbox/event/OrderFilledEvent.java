package com.pocketstock.ledger.outbox.event;

import java.math.BigDecimal;

/**
 * 비동기 체결 이벤트(#204) — topic=trading.order.filled. 소수점 배치체결·온주 지정가 체결·배치거부.
 * 동기 체결(즉시체결·취소·거부)은 발행 안 함(REST 응답·거래내역에 남음). core가 구독해 알림함 + PWA 푸시.
 */
public record OrderFilledEvent(
        String eventId,
        Long userId,
        String stockCode,
        String side,         // BUY | SELL
        String fillType,     // FRACTIONAL(소수점 배치) | LIMIT(온주 지정가)
        String status,       // FILLED | REJECTED
        BigDecimal quantity,
        BigDecimal fillPrice,
        BigDecimal amount,
        String currency,
        String occurredAt    // ISO-8601 (KST)
) {
    public static final String TOPIC = "trading.order.filled";
    public static final String AGGREGATE = "ORDER";
}
