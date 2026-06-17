package com.pocketstock.user.member.dto;

/** 비밀번호 실시간 검증 요청. */
public record PasswordValidateRequest(String password) {

    // 민감정보(비밀번호)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "PasswordValidateRequest{password=***MASKED***}";
    }
}
