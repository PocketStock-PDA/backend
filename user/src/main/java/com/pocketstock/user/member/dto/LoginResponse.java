package com.pocketstock.user.member.dto;

/**
 * 로그인 응답(바디).
 * accessToken : 짧은 수명(JWT, 무상태) — 매 요청 Authorization: Bearer 로 사용
 * expiresIn   : accessToken 유효시간(초)
 *
 * <p>refreshToken은 바디로 내려가지 않는다 — HttpOnly 쿠키(Set-Cookie)로만 전달해
 * JS 접근을 막는다(XSS 시 탈취 방지). {@link AuthTokens} 참고.
 */
public record LoginResponse(String accessToken, long expiresIn) {}
