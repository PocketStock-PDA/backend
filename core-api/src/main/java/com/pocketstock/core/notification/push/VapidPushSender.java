package com.pocketstock.core.notification.push;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * 표준 Web Push(VAPID) 발송. nl.martijndwars:web-push 사용.
 * 발송 실패는 예외로 던지지 않고 PushResult로만 반환 — 알림 생성/INSERT를 깨지 않게.
 */
@Slf4j
@Component
public class VapidPushSender implements PushSender {

    private final PushService pushService;
    // 프론트의 PushSubscription.toJSON()은 expirationTime 등 부가 필드를 포함하므로 unknown 무시
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public VapidPushSender(
            @Value("${webpush.vapid.public-key}") String publicKey,
            @Value("${webpush.vapid.private-key}") String privateKey,
            @Value("${webpush.vapid.subject}") String subject) throws GeneralSecurityException {
        Security.addProvider(new BouncyCastleProvider());
        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    @Override
    public PushResult send(String subscriptionJson, String title, String body) {
        try {
            Subscription subscription = objectMapper.readValue(subscriptionJson, Subscription.class);
            String payload = objectMapper.writeValueAsString(new PushPayload(title, body));

            HttpResponse response = pushService.send(new Notification(subscription, payload));
            int status = response.getStatusLine().getStatusCode();

            if (status == 404 || status == 410) {
                log.info("만료된 푸시 구독(status={}) — 토큰 정리 대상", status);
                return PushResult.EXPIRED;
            }
            if (status / 100 == 2) {
                return PushResult.SENT;
            }
            log.warn("웹푸시 발송 비정상 응답: status={}", status);
            return PushResult.FAILED;
        } catch (Exception e) {
            log.warn("웹푸시 발송 실패: {}", e.getMessage());
            return PushResult.FAILED;
        }
    }

    /** Service Worker가 수신해 표시할 페이로드. */
    private record PushPayload(String title, String body) {}
}
