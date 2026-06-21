package com.pocketstock.user.member.dto;

/**
 * 로그인 성공 시 서비스가 발급하는 토큰 묶음(내부 전용).
 * 컨트롤러가 accessToken·expiresIn은 응답 바디({@link LoginResponse})로,
 * refreshToken은 HttpOnly 쿠키로 분배한다(refreshToken은 바디로 노출하지 않는다).
 */
public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {}
