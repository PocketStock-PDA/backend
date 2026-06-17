package com.pocketstock.user.member.dto;

/**
 * 회원가입 요청.
 * residentFront(주민번호 앞 6자리, YYMMDD) + residentBack(뒤 1자리)로 생년월일·성별을 도출한다.
 */
public record SignupRequest(
        String username,
        String password,
        String name,
        String residentFront,
        String residentBack,
        String phone
) {
    // 민감정보(비밀번호·주민번호)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "SignupRequest{username=" + username
                + ", password=***MASKED***"
                + ", name=" + name
                + ", residentFront=***MASKED***"
                + ", residentBack=***MASKED***"
                + ", phone=" + phone + '}';
    }
}
