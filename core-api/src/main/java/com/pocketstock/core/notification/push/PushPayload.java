package com.pocketstock.core.notification.push;

import java.util.Map;

/**
 * 서비스워커가 수신해 표시할 푸시 페이로드(#204).
 * {@code title}/{@code body}는 폴백, {@code data}는 FE가 앱 화면과 동일하게 렌더하기 위한 구조화 필드.
 * 숫자는 raw(콤마/통화기호 X) — 포맷은 FE가 {@code currency}와 함께 처리.
 *
 * <p>null 필드는 직렬화에서 제외(NON_NULL) — 폴백 알림은 {@code data} 없이 title/body만 나간다.
 */
public record PushPayload(
        String type,          // TRADE_FILLED | UNFILLED | ... (FE 분기 어휘)
        String title,         // 폴백 표시
        String body,          // 폴백 표시
        String tag,           // 그룹·덮어쓰기 키 (예: order-12345)
        String url,           // 딥링크(없으면 FE가 type 기반 파생)
        String occurredAt,    // ISO-8601 UTC (예: 2026-06-29T02:08:00Z)
        Map<String, Object> data) {

    /** 데이터 없는 폴백 — title/body(+선택 type)만. */
    public static PushPayload basic(String type, String title, String body) {
        return new PushPayload(type, title, body, null, null, null, null);
    }
}
