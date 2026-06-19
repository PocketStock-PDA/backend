package com.pocketstock.core.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 알림 수신 설정 요청.
 * priceAlert ↔ notify_unfilled(미체결) 컬럼에 매핑된다.
 */
public record NotificationSettingsRequest(
        @NotNull Boolean tradeFilled,
        @NotNull Boolean priceAlert,
        @NotNull Boolean goalNudge,
        @NotNull Boolean marketing
) {}
