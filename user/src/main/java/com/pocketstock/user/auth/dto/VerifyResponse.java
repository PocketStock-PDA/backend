package com.pocketstock.user.auth.dto;

/** 인증 대조 결과 — 난수문자/SMS 공용. */
public record VerifyResponse(boolean verified) {
}
