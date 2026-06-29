package com.pocketstock.ledger.outbox.event;

import java.math.BigDecimal;

/**
 * 퍼즐 100조각 달성 이벤트 — topic=trading.puzzle.complete.
 * 소수점 배치체결 후 fractional_qty ≥ 1.0이 된 사용자에게 발행.
 * core가 구독해 알림함 + PWA 푸시.
 */
public record PuzzleCompleteEvent(
        String eventId,
        Long userId,
        String stockCode,
        String stockName,
        BigDecimal fractionalQty,  // 달성 시점 소수 보유량
        String occurredAt          // ISO-8601 (KST)
) {
    public static final String TOPIC = "trading.puzzle.complete";
    public static final String AGGREGATE = "PUZZLE";
}
