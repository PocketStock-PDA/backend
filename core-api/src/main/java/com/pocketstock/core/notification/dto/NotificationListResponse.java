package com.pocketstock.core.notification.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationItem> notifications,
        long unreadCount,
        int page,
        long totalElements
) {}
