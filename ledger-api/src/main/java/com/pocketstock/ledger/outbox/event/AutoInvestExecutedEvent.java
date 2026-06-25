package com.pocketstock.ledger.outbox.event;

import java.math.BigDecimal;

/**
 * 자동모으기 집행 이벤트(#204) — topic=autoinvest.executed. 정기매수·물타기·익절 집행/실패.
 * core가 구독해 알림함 + PWA 푸시. trigger로 메시지 분기(정기/물타기/익절), status로 성공/실패.
 */
public record AutoInvestExecutedEvent(
        String eventId,
        Long userId,
        String stockCode,
        String trigger,       // PERIODIC | DIP_BUY | TAKE_PROFIT
        String side,          // BUY | SELL
        String status,        // ACCEPTED(접수·체결 대기) | QUEUED | FILLED | FAILED
        BigDecimal amount,    // 체결/접수 금액(없으면 null)
        BigDecimal quantity,  // 수량(없으면 null)
        String failReason,    // FAILED 사유(없으면 null)
        String currency,
        String occurredAt     // ISO-8601 (KST)
) {
    public static final String TOPIC = "autoinvest.executed";
    public static final String AGGREGATE = "AUTO_INVEST";
}
