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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationSettingMapper notificationSettingMapper;

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
