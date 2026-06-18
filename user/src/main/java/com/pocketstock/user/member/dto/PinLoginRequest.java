package com.pocketstock.user.member.dto;

/** PIN/패턴 간편 로그인 요청. type = PIN | PATTERN. 사용자 식별은 X-Device-Id 헤더로 한다. */
public record PinLoginRequest(String type, String value) {

    // 민감정보(PIN/패턴 값)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "PinLoginRequest{type=" + type + ", value=***MASKED***}";
    }
}
