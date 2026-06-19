package com.pocketstock.core.notification.dto;

public record MarkReadResponse(
        Long id,
        boolean isRead
) {}
