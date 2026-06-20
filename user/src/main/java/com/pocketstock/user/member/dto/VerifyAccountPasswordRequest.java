package com.pocketstock.user.member.dto;

/**
 * 거래 인증(계좌 비밀번호 검증) 요청.
 *
 * <p>{@code keepAuth}("비밀번호 유지" 토글) = true면 검증 성공을 30분 거래 세션으로 기억해
 * 그 안의 거래는 비밀번호를 다시 묻지 않는다. false면 이번 1회만 통과하고 세션을 남기지 않는다.
 */
public record VerifyAccountPasswordRequest(String accountPassword, boolean keepAuth) {

    // 민감정보(계좌 비밀번호)는 로그·예외 메시지 유출 방지를 위해 마스킹
    @Override
    public String toString() {
        return "VerifyAccountPasswordRequest{accountPassword=***MASKED***, keepAuth=" + keepAuth + "}";
    }
}
