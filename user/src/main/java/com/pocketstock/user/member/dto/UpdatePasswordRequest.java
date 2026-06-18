package com.pocketstock.user.member.dto;

/** 회원 비밀번호 수정 요청. 현재 비밀번호 확인 후 새 비밀번호로 변경한다. */
public record UpdatePasswordRequest(String currentPassword, String newPassword) {

    // 민감정보(비밀번호)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "UpdatePasswordRequest{currentPassword=***MASKED***, newPassword=***MASKED***}";
    }
}
