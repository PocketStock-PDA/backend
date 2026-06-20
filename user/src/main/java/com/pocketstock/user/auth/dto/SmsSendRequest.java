package com.pocketstock.user.auth.dto;

/** SMS 인증번호 발송 요청(mock — 실제 발송 없이 서버 보관, 코드는 로그 출력). */
public record SmsSendRequest(String phone) {
}
