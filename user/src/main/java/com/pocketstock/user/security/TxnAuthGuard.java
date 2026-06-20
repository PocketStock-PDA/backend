package com.pocketstock.user.security;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 거래 인증 가드 — 계좌 비밀번호 30분 세션({@link TxnAuth})을 검증한다. 환전·CMA·주문/이체가 공유한다.
 *
 * <p>거래 실행 전 {@link #requireTxnAuth(Long)}를 호출해 Redis {@code txn-auth:{userId}} 키 존재를
 * 확인한다(2콜 모델: 거래 전 회원 도메인의 {@code /verify}로 키를 선행 발급). 검증만 담당하며,
 * raw 비밀번호를 다루거나 키를 발급하지 않는다 — 발급은 회원 도메인의 거래 인증 API 책임.
 *
 * <p>{@code user.security}에 두어 core-api·ledger-api 양쪽 스캔 대상이며, 두 앱 모두 Redis 빈을 가진다.
 */
@Component
@RequiredArgsConstructor
public class TxnAuthGuard {

    private final StringRedisTemplate redis;

    /** 거래 인증 필요 — 미인증(키 없음/만료) 시 {@code TXN_AUTH_REQUIRED}. */
    public void requireTxnAuth(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!Boolean.TRUE.equals(redis.hasKey(TxnAuth.key(userId)))) {
            throw new BusinessException(ErrorCode.TXN_AUTH_REQUIRED);
        }
    }
}
