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

    /**
     * 설정 row가 없는 신규 유저용 기본값. notification_settings 컬럼 DEFAULT(전부 TRUE)와 일치시킨다.
     * GET은 읽기 전용이라 row를 만들지 않으며, 실제 row는 최초 토큰 등록/설정 저장 시 생성된다.
     */
    public static NotificationSettingsResponse defaults() {
        return new NotificationSettingsResponse(true, true, true, true);
    }
}
