package com.pocketstock.ledger.trading.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 체결통보 발행기(#139) — 비동기 체결 확정 시 해당 유저 토픽으로 STOMP push.
 *
 * <p>토픽 = {@code /topic/order-notification/{userId}} (user destination prefix 미사용 → userId 토픽 규약, CLAUDE.md).
 * 영속 알림(알림함+PWA 푸시)은 Kafka→core(#204)가 담당하고, 이건 "앱 켜져 있을 때 즉시 화면 반영"용 비영속 출구.
 * 같은 체결 지점에서 outbox(#204)와 나란히 호출 — WS는 fire-and-forget(미연결 유저면 그냥 버려짐).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationPublisher {

    private static final String TOPIC_PREFIX = "/topic/order-notification/";

    private final SimpMessagingTemplate messagingTemplate;

    /** 체결통보 push — 미연결·실패해도 체결 트랜잭션에 영향 없게 예외를 삼킨다(best-effort). */
    public void push(Long userId, OrderNotification payload) {
        if (userId == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(TOPIC_PREFIX + userId, payload);
        } catch (Exception e) {
            log.warn("[체결통보] WS push 실패 userId={} orderId={}", userId, payload.orderId(), e);
        }
    }
}
