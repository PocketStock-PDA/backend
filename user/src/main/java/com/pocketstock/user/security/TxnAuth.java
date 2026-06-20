package com.pocketstock.user.security;

import java.time.Duration;

/**
 * 거래 인증(계좌 비밀번호) 세션 규약 — 발급(user.member)·검증({@link TxnAuthGuard})이 공유한다.
 *
 * <p>계좌 비밀번호 검증 성공을 Redis에 {@code txn-auth:{userId}} 키로 일정 시간 기록해,
 * 그 시간 안의 거래(환전·CMA·주문/이체)는 비밀번호 재입력 없이 통과시킨다("비밀번호 유지" 토글).
 * 키 발급은 회원 도메인의 거래 인증 API가, 키 확인은 각 거래 도메인이 {@link TxnAuthGuard}로 한다.
 */
public final class TxnAuth {

    /** Redis 키 접두사. 최종 키 = {@code KEY_PREFIX + userId}. */
    public static final String KEY_PREFIX = "txn-auth:";

    /** 거래 인증 유지 시간("비밀번호 유지" 토글 ON 시). */
    public static final Duration TTL = Duration.ofMinutes(30);

    private TxnAuth() {
    }

    public static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
