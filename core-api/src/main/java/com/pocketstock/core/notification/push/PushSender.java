package com.pocketstock.core.notification.push;

/**
 * 웹푸시 발송 추상화. 구현(VAPID/FCM 등)을 교체 가능하게 분리.
 */
public interface PushSender {

    /**
     * @param subscriptionJson notification_settings.push_token에 저장된 구독 JSON
     *                         ({"endpoint":..., "keys":{"p256dh":..., "auth":...}})
     */
    PushResult send(String subscriptionJson, String title, String body);
}
