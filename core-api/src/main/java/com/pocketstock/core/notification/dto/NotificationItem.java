package com.pocketstock.core.notification.dto;

import java.time.LocalDateTime;

public record NotificationItem(
        Long id,
        String type,
        String title,
        String body,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationItem from(NotificationRow row) {
        return new NotificationItem(
                row.getId(),
                row.getType(),
                row.getTitle(),
                row.getBody(),
                row.isRead(),
                row.getCreatedAt()
        );
    }
}
