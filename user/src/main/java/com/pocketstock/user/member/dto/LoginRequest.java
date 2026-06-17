package com.pocketstock.user.member.dto;

/** ID/PW 로그인 요청. */
public record LoginRequest(String username, String password) {

    // 비밀번호는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "LoginRequest{username=" + username + ", password=***MASKED***}";
    }
}
