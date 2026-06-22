package com.pocketstock.core.asset.dto;

/** 통합인증 응답 — 무상태 mock 토큰. 서버는 검증·저장하지 않는다. */
public record LinkAuthResponse(String authToken) {
}
