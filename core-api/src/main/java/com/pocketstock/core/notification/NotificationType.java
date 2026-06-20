package com.pocketstock.core.notification;

import com.pocketstock.core.notification.dto.NotificationSettingRow;

/**
 * 알림 종류 ↔ 수신 설정 토글 매핑.
 * 타 도메인은 이 enum으로 create(...)를 호출한다.
 */
public enum NotificationType {

    TRADE_FILLED {   // 주문 체결
        @Override public boolean enabledFor(NotificationSettingRow s) { return s.isNotifyTrade(); }
    },
    UNFILLED {       // 미체결 (priceAlert)
        @Override public boolean enabledFor(NotificationSettingRow s) { return s.isNotifyUnfilled(); }
    },
    GOAL_NUDGE {     // 목표 알림
        @Override public boolean enabledFor(NotificationSettingRow s) { return s.isNotifyGoal(); }
    },
    MARKETING {      // 마케팅
        @Override public boolean enabledFor(NotificationSettingRow s) { return s.isNotifyMarketing(); }
    },
    ACCOUNT_VERIFY { // 계좌 1원 인증 코드 — 보안성 알림이라 토글과 무관하게 항상 발송
        @Override public boolean enabledFor(NotificationSettingRow s) { return true; }
    };

    /** 이 사용자의 설정에서 해당 타입 푸시가 켜져 있는지. */
    public abstract boolean enabledFor(NotificationSettingRow s);
}
