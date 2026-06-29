package com.pocketstock.core.notification.dto;

/** 개발용 푸시 검수 응답 — sent=발송 건수(0/1), result=발송 결과(SENT/FAILED). */
public record PushTestResponse(int sent, String result) {
}
