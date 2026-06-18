package com.pocketstock.user.member.dto;

/** 비밀번호 재설정 요청. 아이디+휴대폰 본인확인 후 새 비밀번호로 변경한다. */
public record ResetPasswordRequest(String username, String phone, String newPassword) {

    // 민감정보(비밀번호)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "ResetPasswordRequest{username=" + username
                + ", phone=" + phone
                + ", newPassword=***MASKED***}";
    }
}
