package com.pocketstock.core.notification.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 푸시 토큰 등록 요청.
 * token: FCM 토큰 또는 Web Push(VAPID) 구독을 JSON.stringify 한 문자열.
 * deviceType: ANDROID / IOS / WEB
 */
public record PushTokenRequest(
        @NotBlank String token,
        @NotBlank String deviceType
) {}
