package com.pocketstock.core.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.core.notification.NotificationService;
import com.pocketstock.core.notification.NotificationType;
import com.pocketstock.core.notification.mapper.ProcessedEventMapper;
import com.pocketstock.core.notification.push.PushPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");   // 이벤트 occurredAt 원천 타임존

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
        String stockName = e.path("stockName").asText(stockCode);   // 구버전 메시지 폴백
        String side = e.path("side").asText();
        String currency = e.path("currency").asText(null);
        Long orderId = refIdFromEventId(e.path("eventId").asText(""), 1);   // order:{orderId}:filled
        String occurredAt = toUtc(e.path("occurredAt").asText(""));
        String tag = orderId != null ? "order-" + orderId : null;
        boolean filled = "FILLED".equals(e.path("status").asText());
        if (filled) {
            BigDecimal qty = dec(e.path("quantity"));
            String title = sideLabel(side) + " 체결";
            String body = stockName + " " + plain(qty) + "주 체결";
            // FE 렌더용 — 숫자는 raw, 포맷은 FE 담당. url은 FE가 type 기반 파생(생략).
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("side", side);
            data.put("stockCode", stockCode);
            data.put("stockName", stockName);
            data.put("quantity", qty);
            data.put("fillPrice", dec(e.path("fillPrice")));
            data.put("totalAmount", dec(e.path("amount")));
            data.put("currency", currency);
            data.put("orderId", orderId);
            PushPayload push = new PushPayload(NotificationType.TRADE_FILLED.name(),
                    title, body, tag, null, occurredAt, data);
            notificationService.create(userId, NotificationType.TRADE_FILLED, title, body, "ORDER", orderId, push);
        } else {
            BigDecimal qty = dec(e.path("quantity"));
            String title = sideLabel(side) + " 주문 실패";
            String body = stockName + " 주문이 체결되지 못했어요";
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("side", side);
            data.put("stockCode", stockCode);
            data.put("stockName", stockName);
            data.put("quantity", qty);
            data.put("currency", currency);
            data.put("orderId", orderId);
            PushPayload push = new PushPayload(NotificationType.UNFILLED.name(),
                    title, body, tag, null, occurredAt, data);
            notificationService.create(userId, NotificationType.UNFILLED, title, body, "ORDER", orderId, push);
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
        String eventId = e.path("eventId").asText("");
        String stockCode = e.path("stockCode").asText();
        String trigger = e.path("trigger").asText();
        String stockName = fallback(e.path("stockName").asText(null), stockCode);
        String side = e.path("side").asText(null);
        String status = e.path("status").asText();
        String currency = e.path("currency").asText(null);
        Long settingId = e.path("settingId").isNumber()
                ? e.path("settingId").asLong()
                : refIdFromEventId(eventId, 2);   // autoinvest:exec:{stockId}:{roundNo}
        Long parsedRoundNo = refIdFromEventId(eventId, 3);
        Integer roundNo = e.path("roundNo").isNumber()
                ? e.path("roundNo").asInt()
                : (parsedRoundNo == null ? null : parsedRoundNo.intValue());
        String occurredAt = toUtc(e.path("occurredAt").asText(""));
        String tag = autoInvestTag(settingId, roundNo, eventId);
        String url = stockCode.isBlank() ? null : "/portfolio/detail?stockCode=" + stockCode + "&view=collect";
        boolean failed = "FAILED".equals(status);
        String triggerLabel = triggerLabel(trigger);
        BigDecimal amount = dec(e.path("amount"));
        BigDecimal quantity = dec(e.path("quantity"));
        String reason = fallback(e.path("failReason").asText(null), null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trigger", trigger);
        data.put("side", side);
        data.put("stockCode", stockCode);
        data.put("stockName", stockName);
        data.put("amount", amount);
        data.put("quantity", quantity);
        data.put("currency", currency);
        data.put("status", status);
        data.put("reason", reason);
        data.put("settingId", settingId);
        data.put("roundNo", roundNo);

        if (failed) {
            String title = triggerLabel + " 실패";
            String body = stockName + " " + triggerLabel + " 실패" + (reason == null ? "" : " (" + reason + ")");
            PushPayload push = new PushPayload(NotificationType.AUTO_INVEST_FAILED.name(),
                    title, body, tag, url, occurredAt, data);
            notificationService.create(userId, NotificationType.AUTO_INVEST_FAILED, title, body,
                    "AUTO_INVEST", settingId, push);
        } else {
            String statusLabel = autoInvestStatusLabel(status);
            String detail = amountOrQuantityText(amount, quantity, currency);
            String title = triggerLabel + " " + statusLabel;
            String body = stockName + " " + (detail.isBlank() ? "" : detail + " ")
                    + triggerLabel + " " + autoInvestBodySuffix(status);
            PushPayload push = new PushPayload(NotificationType.AUTO_INVEST_EXECUTED.name(),
                    title, body, tag, url, occurredAt, data);
            notificationService.create(userId, NotificationType.AUTO_INVEST_EXECUTED, title, body,
                    "AUTO_INVEST", settingId, push);
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

    private String autoInvestStatusLabel(String status) {
        return switch (status) {
            case "FILLED" -> "완료";
            case "FAILED" -> "실패";
            default -> "접수";
        };
    }

    private String autoInvestBodySuffix(String status) {
        return switch (status) {
            case "FILLED" -> "완료되었어요";
            default -> "접수되었어요";
        };
    }

    /** JSON 숫자 노드 → BigDecimal(없거나 null이면 null — payload에서 직렬화 제외). */
    private BigDecimal dec(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    /** 폴백 body용 수량 표기 — 끝자리 0 제거(예: 0.140 → 0.14). */
    private String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    /** 이벤트 occurredAt(UTC Z 또는 KST 로컬) → UTC ISO-8601(Z). 파싱 실패 시 null(알림은 정상 발송). */
    private String toUtc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value).toString();
        } catch (Exception ex) {
            try {
                return LocalDateTime.parse(value).atZone(KST).toInstant().toString();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String amountOrQuantityText(BigDecimal amount, BigDecimal quantity, String currency) {
        if (amount != null) {
            String v = plain(amount);
            if ("KRW".equals(currency)) {
                return v + "원";
            }
            if ("USD".equals(currency)) {
                return "$" + v;
            }
            return v + (currency == null || currency.isBlank() ? "" : " " + currency);
        }
        if (quantity != null) {
            return plain(quantity) + "주";
        }
        return "";
    }

    private String autoInvestTag(Long settingId, Integer roundNo, String eventId) {
        if (settingId != null && roundNo != null) {
            return "autoinvest-" + settingId + "-" + roundNo;
        }
        return eventId == null || eventId.isBlank() ? null : eventId.replace(':', '-');
    }

    private String fallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
