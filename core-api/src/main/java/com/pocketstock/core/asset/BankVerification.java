package com.pocketstock.core.asset;

import java.time.Duration;

/**
 * 계좌 1원 인증 챌린지 규약 — Redis 저장(휘발성)·정책 상수.
 *
 * <p>송금요청 시 4자리 코드를 생성해 해시 키 {@code bank-verify:{userId}:{accountId}}에
 * {@code code}/{@code attempts} 필드로 저장하고 {@link #TTL} 후 자동 만료시킨다(별도 만료 스케줄 불필요).
 * 코드 자체는 응답으로 내려주지 않고 웹푸시(목 1원 입금 알림)로만 전달한다. 확인 단계에서 코드 일치 시
 * {@code linked_bank_accounts.is_verified}를 마킹한다. 거래 인증(txn-auth)과는 별개 메커니즘이다.
 */
public final class BankVerification {

    /** Redis 키 = {@code KEY_PREFIX + userId + ":" + accountId}. */
    public static final String KEY_PREFIX = "bank-verify:";

    /** 해시 필드 — 인증 코드. */
    public static final String FIELD_CODE = "code";

    /** 해시 필드 — 누적 시도 횟수. */
    public static final String FIELD_ATTEMPTS = "attempts";

    /** 코드 유효시간. */
    public static final Duration TTL = Duration.ofMinutes(5);

    /** 최대 확인 시도 횟수(초과 시 챌린지 폐기 → 재요청 필요). */
    public static final int MAX_ATTEMPTS = 5;

    /** 코드 자리수. */
    public static final int CODE_DIGITS = 4;

    /** 목 1원 입금자명 표기(은행 거래내역에 찍히는 이름 시뮬). */
    public static final String SENDER_NAME = "포켓스톡";

    private BankVerification() {
    }

    public static String key(Long userId, Long accountId) {
        return KEY_PREFIX + userId + ":" + accountId;
    }
}
