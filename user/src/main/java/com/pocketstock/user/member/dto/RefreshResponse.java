package com.pocketstock.user.member.dto;

/** 토큰 재발급 응답 — 새 accessToken만 발급(refreshToken은 그대로 유지). */
public record RefreshResponse(String accessToken, long expiresIn) {}
