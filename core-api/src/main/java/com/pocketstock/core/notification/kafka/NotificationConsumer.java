package com.pocketstock.core.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.core.notification.NotificationService;
import com.pocketstock.core.notification.NotificationType;
import com.pocketstock.core.notification.mapper.ProcessedEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 비동기 알림 이벤트 구독(#204) — ledger가 Kafka로 발행한 체결·자동모으기 이벤트를 받아 알림함 저장 + PWA 푸시.
 *
 * <p>at-least-once라 같은 event_id가 중복 배달될 수 있어, {@link ProcessedEventMapper}로 멱등 처리(이미 처리한
 * 이벤트는 스킵). payload는 ledger의 이벤트 JSON — 필드 일부만 읽어 알림 메시지로 변환(스키마 결합 최소화).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedEventMapper processedEventMapper;
    private final NotificationService notificationService;

    /** 체결 알림 — trading.order.filled (소수점 배치체결·온주 지정가체결·배치거부). */
    @KafkaListener(topics = "trading.order.filled", groupId = "core-notification")
    @Transactional
    public void onOrderFilled(String message) {
        JsonNode e = parse(message);
        if (e == null || !firstTime(e)) {
            return;
        }
        Long userId = e.path("userId").asLong();
        String stockCode = e.path("stockCode").asText();
        String side = e.path("side").asText();
        Long orderId = refIdFromEventId(e.path("eventId").asText(""), 1);   // order:{orderId}:filled
        boolean filled = "FILLED".equals(e.path("status").asText());
        if (filled) {
            String qty = e.path("quantity").asText("");
            String price = num(e.path("fillPrice").asText(""));
            notificationService.create(userId, NotificationType.TRADE_FILLED,
                    sideLabel(side) + " 체결",
                    stockCode + " " + qty + "주 @ " + price + " 체결되었어요",
                    "ORDER", orderId);
        } else {
            notificationService.create(userId, NotificationType.UNFILLED,
                    sideLabel(side) + " 주문 실패",
                    stockCode + " 주문이 체결되지 못했어요",
                    "ORDER", orderId);
        }
    }

    /** 자동모으기 알림 — autoinvest.executed (정기매수·물타기·익절 집행/실패). */
    @KafkaListener(topics = "autoinvest.executed", groupId = "core-notification")
    @Transactional
    public void onAutoInvestExecuted(String message) {
        JsonNode e = parse(message);
        if (e == null || !firstTime(e)) {
            return;
        }
        Long userId = e.path("userId").asLong();
        String stockCode = e.path("stockCode").asText();
        String trigger = e.path("trigger").asText();
        Long stockId = refIdFromEventId(e.path("eventId").asText(""), 2);   // autoinvest:exec:{stockId}:{roundNo}
        boolean failed = "FAILED".equals(e.path("status").asText());
        String triggerLabel = triggerLabel(trigger);
        if (failed) {
            String reason = e.path("failReason").asText("");
            notificationService.create(userId, NotificationType.UNFILLED,
                    triggerLabel + " 실패",
                    stockCode + " " + triggerLabel + "이(가) 실패했어요" + (reason.isBlank() ? "" : " (" + reason + ")"),
                    "AUTO_INVEST", stockId);
        } else {
            String amount = num(e.path("amount").asText(""));
            notificationService.create(userId, NotificationType.TRADE_FILLED,
                    triggerLabel + " 완료",
                    stockCode + " " + (amount.isBlank() ? "" : amount + "원 ") + triggerLabel + " 접수되었어요",
                    "AUTO_INVEST", stockId);
        }
    }

    /** eventId(콜론 구분)에서 idx번째 토막을 Long ref_id로. 파싱 실패 시 null(딥링크만 비고 알림은 정상). */
    private Long refIdFromEventId(String eventId, int idx) {
        try {
            String[] parts = eventId.split(":");
            return idx < parts.length ? Long.valueOf(parts[idx]) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 멱등 — 처음 처리하는 event_id면 true(마킹 성공), 중복이면 false(스킵). */
    private boolean firstTime(JsonNode e) {
        String eventId = e.path("eventId").asText(null);
        if (eventId == null) {
            return false;
        }
        return processedEventMapper.markProcessed(eventId) == 1;
    }

    private JsonNode parse(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception ex) {
            log.error("[알림consumer] 이벤트 파싱 실패 — {}", message, ex);
            return null;   // 파싱 불가 메시지는 ack(독약 메시지 무한재시도 방지)
        }
    }

    private String sideLabel(String side) {
        return "SELL".equals(side) ? "매도" : "매수";
    }

    private String triggerLabel(String trigger) {
        return switch (trigger) {
            case "DIP_BUY" -> "물타기";
            case "TAKE_PROFIT" -> "익절";
            default -> "자동모으기";
        };
    }

    /** 숫자 천단위 콤마(파싱 실패 시 원문). */
    private String num(String v) {
        if (v == null || v.isBlank()) {
            return "";
        }
        try {
            return new BigDecimal(v).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return v;
        }
    }
}
