package com.pocketstock.core.notification.push;

import com.pocketstock.core.notification.dto.NotificationSettingRow;
import com.pocketstock.core.notification.mapper.NotificationSettingMapper;
import com.pocketstock.user.notification.PushNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link PushNotifier} 포트의 VAPID 웹푸시 어댑터.
 * user 모듈(잠금 해제 등)이 호출하면 notification_settings의 push_token으로 직접 발송한다.
 *
 * <p>{@code NotificationService.create()}와 달리 알림함에 INSERT하지 않고 토글도 보지 않는다
 * — 인증번호 같은 1회성 보안 알림을 이력에 남기지 않고, 알림 OFF여도 전달하기 위함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushNotifier implements PushNotifier {

    private final NotificationSettingMapper notificationSettingMapper;
    private final PushSender pushSender;

    @Override
    public boolean sendToUser(Long userId, String title, String body) {
        NotificationSettingRow setting = notificationSettingMapper.findByUserId(userId);
        if (setting == null) return false;                              // 설정 없음 → 미구독

        String token = setting.getPushToken();
        if (token == null || token.isBlank()) return false;            // 미구독
        if (!"WEB".equalsIgnoreCase(setting.getPlatform())) return false;  // 현재 WEB(VAPID)만 지원

        PushResult result = pushSender.send(token, title, body);
        if (result == PushResult.EXPIRED) {
            try {
                notificationSettingMapper.clearToken(userId);          // 만료 구독 정리 — best-effort
            } catch (Exception e) {
                log.warn("만료 구독 토큰 정리 실패(userId={}): {}", userId, e.getMessage());
            }
            return false;
        }
        return result == PushResult.SENT;
    }
}
