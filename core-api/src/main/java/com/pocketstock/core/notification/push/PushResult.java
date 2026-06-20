package com.pocketstock.core.notification.push;

/**
 * 푸시 발송 결과.
 * EXPIRED: 구독이 만료/무효(404·410) → 저장된 토큰을 정리해야 함.
 */
public enum PushResult {
    SENT,
    EXPIRED,
    FAILED
}
