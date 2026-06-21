package com.pocketstock.user.auth.dto;

/** SMS 인증번호 확인 요청. */
public record SmsVerifyRequest(String phone, String code) {
}
