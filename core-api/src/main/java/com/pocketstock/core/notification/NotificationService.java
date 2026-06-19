package com.pocketstock.core.notification;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.notification.dto.MarkAllReadResponse;
import com.pocketstock.core.notification.dto.MarkReadResponse;
import com.pocketstock.core.notification.dto.NotificationItem;
import com.pocketstock.core.notification.dto.NotificationListResponse;
import com.pocketstock.core.notification.dto.NotificationSettingRow;
import com.pocketstock.core.notification.dto.NotificationSettingsRequest;
import com.pocketstock.core.notification.dto.NotificationSettingsResponse;
import com.pocketstock.core.notification.dto.PushTokenRequest;
import com.pocketstock.core.notification.mapper.NotificationMapper;
import com.pocketstock.core.notification.mapper.NotificationSettingMapper;
import com.pocketstock.core.notification.push.PushResult;
import com.pocketstock.core.notification.push.PushSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationSettingMapper notificationSettingMapper;
    private final PushSender pushSender;

    /**
     * 알림 생성 + (수신 설정 허용 시) 웹푸시 발송. 타 도메인(체결·목표 등)이 호출.
     * 호출자 트랜잭션이 있으면 커밋 성공 후에만 생성·발송한다(롤백 시 알림·푸시 미발생).
     * 발송 실패는 알림 기록을 막지 않는다(알림함엔 항상 남김).
     */
    public void create(Long userId, NotificationType type, String title, String body) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doCreate(userId, type, title, body);
                }
            });
        } else {
            doCreate(userId, type, title, body);
        }
    }

    private void doCreate(Long userId, NotificationType type, String title, String body) {
        notificationMapper.insert(userId, type.name(), title, body);   // 1) 알림함 기록

        NotificationSettingRow setting = notificationSettingMapper.findByUserId(userId);
        if (setting == null) return;                       // 설정 없음 → 푸시 생략
        if (!type.enabledFor(setting)) return;             // 토글 OFF → 푸시 생략

        String token = setting.getPushToken();
        if (token == null || token.isBlank()) return;      // 미구독
        if (!"WEB".equalsIgnoreCase(setting.getPlatform())) return;  // 현재 WEB(VAPID)만 발송

        PushResult result = pushSender.send(token, title, body);     // 2) 발송
        if (result == PushResult.EXPIRED) {
            try {
                notificationSettingMapper.clearToken(userId);        // 만료 구독 정리 — best-effort
            } catch (Exception e) {
                log.warn("만료 구독 토큰 정리 실패(userId={}): {}", userId, e.getMessage());
            }
        }
    }

    public NotificationListResponse getNotifications(Long userId, Boolean read, int page, int size) {
        int offset = page * size;

        List<NotificationItem> items = notificationMapper.findByUser(userId, read, size, offset)
                .stream()
                .map(NotificationItem::from)
                .toList();

        long unreadCount   = notificationMapper.countUnread(userId);
        long totalElements = notificationMapper.countByUser(userId, read);

        return new NotificationListResponse(items, unreadCount, page, totalElements);
    }

    @Transactional
    public MarkReadResponse markRead(Long userId, Long id) {
        int updated = notificationMapper.markRead(id, userId);

        // 영향 0행: 본인 소유가 아니면 404, 이미 읽은 본인 알림이면 멱등 처리.
        if (updated == 0 && !notificationMapper.existsByIdAndUser(id, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        return new MarkReadResponse(id, true);
    }

    @Transactional
    public MarkAllReadResponse markAllRead(Long userId) {
        int updatedCount = notificationMapper.markAllRead(userId);
        return new MarkAllReadResponse(updatedCount);
    }

    @Transactional
    public void registerToken(Long userId, PushTokenRequest request) {
        notificationSettingMapper.upsertToken(userId, request.token(), request.deviceType());
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(Long userId, NotificationSettingsRequest request) {
        // priceAlert ↔ notify_unfilled(미체결) 매핑
        notificationSettingMapper.upsertSettings(
                userId,
                request.tradeFilled(),
                request.priceAlert(),
                request.goalNudge(),
                request.marketing()
        );

        NotificationSettingRow row = notificationSettingMapper.findByUserId(userId);
        return NotificationSettingsResponse.from(row);
    }
}
