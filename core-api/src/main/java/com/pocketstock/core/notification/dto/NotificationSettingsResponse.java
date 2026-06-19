package com.pocketstock.core.notification.dto;

public record NotificationSettingsResponse(
        boolean tradeFilled,
        boolean priceAlert,
        boolean goalNudge,
        boolean marketing
) {
    public static NotificationSettingsResponse from(NotificationSettingRow row) {
        return new NotificationSettingsResponse(
                row.isNotifyTrade(),
                row.isNotifyUnfilled(),
                row.isNotifyGoal(),
                row.isNotifyMarketing()
        );
    }
}
