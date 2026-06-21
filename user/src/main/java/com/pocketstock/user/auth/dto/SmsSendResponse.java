package com.pocketstock.user.auth.dto;

/**
 * SMS 인증번호 발송 응답(mock).
 * 실제 발송 대신 발급된 코드를 응답으로 내려 프론트가 "문자 도착"을 연출하도록 한다.
 * 운영(실제 SMS 발송) 전환 시 code 필드는 제거한다.
 */
public record SmsSendResponse(String code, int expiresIn) {
}
