package com.pocketstock.core.notification.push;

/**
 * 웹푸시 발송 추상화. 구현(VAPID/FCM 등)을 교체 가능하게 분리.
 */
public interface PushSender {

    /**
     * 구조화 발송(#204) — title/body 폴백 + data envelope.
     * @param subscriptionJson notification_settings.push_token에 저장된 구독 JSON
     *                         ({"endpoint":..., "keys":{"p256dh":..., "auth":...}})
     */
    PushResult send(String subscriptionJson, PushPayload payload);

    /** 폴백 — 보안 1회성 알림(잠금 해제 등)처럼 구조화가 불필요한 경로용. */
    default PushResult send(String subscriptionJson, String title, String body) {
        return send(subscriptionJson, PushPayload.basic(null, title, body));
    }

    /** 개발용 — 임의 JSON payload를 검증·가공 없이 그대로 발송(푸시 포맷 검수 엔드포인트 전용). */
    PushResult sendRaw(String subscriptionJson, String rawJsonPayload);
}
