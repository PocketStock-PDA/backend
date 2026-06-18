package com.pocketstock.user.member.dto;

/** 거래 인증(계좌 비밀번호 검증) 요청. */
public record VerifyAccountPasswordRequest(String accountPassword) {

    // 민감정보(계좌 비밀번호)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "VerifyAccountPasswordRequest{accountPassword=***MASKED***}";
    }
}
