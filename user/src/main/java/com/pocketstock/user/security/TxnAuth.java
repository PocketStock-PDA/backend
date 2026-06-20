package com.pocketstock.user.security;

import java.time.Duration;

/**
 * 거래 인증(계좌 비밀번호) 세션 규약 — 발급(user.member)·검증({@link TxnAuthGuard})이 공유한다.
 *
 * <p>계좌 비밀번호 검증 성공을 Redis에 {@code txn-auth:{userId}} 키로 기록해, 이후 거래(환전·CMA·
 * 주문/이체)가 비밀번호 재입력 없이 통과하게 한다. 검증은 성공 시 <b>항상</b> 키를 발급하되,
 * "비밀번호 유지" 토글에 따라 동작이 갈린다 — 키 <b>값</b>으로 구분한다.
 * <ul>
 *   <li>{@link #VALUE_KEEP}(토글 ON): {@link #TTL}(30분) 동안 유지. 여러 거래가 통과(소비 안 함).</li>
 *   <li>{@link #VALUE_ONCE}(토글 OFF): 직후 <b>1건만</b> 통과. {@link TxnAuthGuard}가 사용 즉시 소비(삭제).
 *       {@link #ONCE_TTL}은 검증→거래 사이를 잇는 안전 유효창(미사용 시 자동 만료).</li>
 * </ul>
 * 키 발급은 회원 도메인의 거래 인증 API가, 키 확인·소비는 각 거래 도메인이 {@link TxnAuthGuard}로 한다.
 */
public final class TxnAuth {

    /** Redis 키 접두사. 최종 키 = {@code KEY_PREFIX + userId}. */
    public static final String KEY_PREFIX = "txn-auth:";

    /** 유지 세션 TTL("비밀번호 유지" 토글 ON). */
    public static final Duration TTL = Duration.ofMinutes(30);

    /** 1회용 인증 유효창(토글 OFF). 검증 후 이 시간 안에 거래 1건을 통과시키고 소비된다. */
    public static final Duration ONCE_TTL = Duration.ofMinutes(5);

    /** 키 값 — 유지 세션(소비 안 함). */
    public static final String VALUE_KEEP = "KEEP";

    /** 키 값 — 1회용(통과 시 소비). */
    public static final String VALUE_ONCE = "ONCE";

    private TxnAuth() {
    }

    public static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
