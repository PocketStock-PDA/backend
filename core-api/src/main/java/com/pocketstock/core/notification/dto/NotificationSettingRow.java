package com.pocketstock.core.notification.dto;

import lombok.Data;

/**
 * notification_settings 테이블 조회 결과 매핑용 row.
 */
@Data
public class NotificationSettingRow {
    private boolean notifyTrade;
    private boolean notifyGoal;
    private boolean notifyUnfilled;
    private boolean notifyMarketing;
    private String pushToken;   // 발송 대상 구독(JSON) — 미구독 시 null
    private String platform;    // WEB / ANDROID / IOS
}
