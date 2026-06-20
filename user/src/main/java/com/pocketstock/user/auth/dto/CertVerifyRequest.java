package com.pocketstock.user.auth.dto;

/** 난수문자 대조 확인 — request에서 받은 randomCode를 echo로 제출. */
public record CertVerifyRequest(String requestId, String randomCode) {
}
