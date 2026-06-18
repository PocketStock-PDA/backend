package com.pocketstock.user.member.dto;

/** PIN/패턴 설정 요청. type = PIN | PATTERN. */
public record SetAuthMethodRequest(String type, String value) {

    // 민감정보(PIN/패턴 값)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "SetAuthMethodRequest{type=" + type + ", value=***MASKED***}";
    }
}
