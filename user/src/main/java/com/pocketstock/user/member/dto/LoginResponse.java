package com.pocketstock.user.member.dto;

/**
 * 로그인 응답.
 * accessToken : 짧은 수명(JWT, 무상태) — 매 요청 인증에 사용
 * refreshToken: 긴 수명(Redis 저장) — accessToken 재발급에 사용
 * expiresIn   : accessToken 유효시간(초)
 */
public record LoginResponse(String accessToken, String refreshToken, long expiresIn) {}
