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

    /**
     * 거래 인증 필요 — 미인증(키 없음/만료) 시 {@code TXN_AUTH_REQUIRED}.
     * 1회용({@link TxnAuth#VALUE_ONCE})은 통과 즉시 소비(삭제)해 다음 거래에서 다시 인증하게 한다.
     * 유지 세션({@link TxnAuth#VALUE_KEEP})은 소비하지 않아 TTL 동안 여러 거래가 통과한다.
     */
    public void requireTxnAuth(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        String key = TxnAuth.key(userId);
        boolean authorized;
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                authorized = false;
            } else if (TxnAuth.VALUE_ONCE.equals(value)) {
                // 1회용: 원자적 읽기+삭제로 소비. 동시 요청이 같은 토큰을 읽어도 getAndDelete가
                // non-null을 돌려준 스레드 하나만 통과(get→delete 사이 TOCTOU 경합 방지).
                authorized = redis.opsForValue().getAndDelete(key) != null;
            } else {
                // VALUE_KEEP: 유지 세션 → 소비하지 않음(TTL 동안 여러 거래 통과).
                authorized = true;
            }
        } catch (RuntimeException e) {
            // Redis 장애 → 인증 확인 불가. 비의도 500 대신 명시적 서버 오류로 응답 계약 고정.
            // (TXN_AUTH_REQUIRED로 바꾸면 재인증을 유도하나 그 역시 Redis라 오인 유발 → 사용 안 함)
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "거래 인증 확인에 실패했습니다.");
        }

        if (!authorized) {
            throw new BusinessException(ErrorCode.TXN_AUTH_REQUIRED);
        }
    }
}
