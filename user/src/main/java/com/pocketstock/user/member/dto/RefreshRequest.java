package com.pocketstock.user.member.dto;

/** 토큰 재발급 요청. */
public record RefreshRequest(String refreshToken) {}
