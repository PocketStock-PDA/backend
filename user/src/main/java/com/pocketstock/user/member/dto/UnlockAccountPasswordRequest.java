package com.pocketstock.user.member.dto;

/**
 * 계좌 비밀번호 잠금 해제 요청 — 등록된 휴대폰으로 푸시 발송된 인증번호.
 *
 * <p>휴대폰 번호는 서버가 로그인 사용자(member.phone)로 결정하므로 클라이언트는 code만 보낸다.
 */
public record UnlockAccountPasswordRequest(String code) {

    // 인증번호는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "UnlockAccountPasswordRequest{code=***MASKED***}";
    }
}
