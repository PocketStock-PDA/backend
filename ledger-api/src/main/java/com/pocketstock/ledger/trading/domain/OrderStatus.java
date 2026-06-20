package com.pocketstock.ledger.trading.domain;

import java.util.Map;
import java.util.Set;

/**
 * 주문 상태머신 — 전이 규칙의 소스 오브 트루스 (ERD-04 §08 소수점 / §08b 온주).
 * DB는 VARCHAR(이름) 저장 + CHECK 제약(안전망), MyBatis가 enum↔VARCHAR를 이름으로 자동 매핑.
 * 부분체결(PARTIAL_FILLED)·이월(CARRIED_OVER) 없음(#101).
 *
 * <pre>
 * 온주  : RECEIVED → FILLED(즉시) | PENDING(지정가 미체결) → FILLED·CANCELLED | REJECTED
 * 소수점: RECEIVED → QUEUED → SENT → FILLED | (QUEUED·PENDING) → CANCELLED | REJECTED
 * </pre>
 */
public enum OrderStatus {
    RECEIVED,   // 접수(검증 전)
    QUEUED,     // 소수점 배치 대기(전송 전 — 취소 가능)
    SENT,       // 소수점 LS 전송됨(취소 불가)
    PENDING,    // 온주 지정가 미체결 대기(취소 가능)
    FILLED,     // 전량 체결
    CANCELLED,  // 사용자 취소
    REJECTED;   // 검증·체결 실패

    /** 허용 전이 맵. 종결 상태는 빈 집합. */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            RECEIVED,  Set.of(QUEUED, SENT, PENDING, FILLED, REJECTED),
            QUEUED,    Set.of(SENT, CANCELLED, REJECTED),
            SENT,      Set.of(FILLED, REJECTED),
            PENDING,   Set.of(FILLED, CANCELLED, REJECTED),
            FILLED,    Set.of(),
            CANCELLED, Set.of(),
            REJECTED,  Set.of()
    );

    /** 이 상태에서 next로의 전이가 허용되는가(전이 가드 ① 앱 레벨). */
    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    /** 더 이상 바뀌지 않는 종결 상태(FILLED/CANCELLED/REJECTED). */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    /** 사용자 취소가 가능한 상태(소수점 QUEUED / 온주 PENDING). */
    public boolean isCancellable() {
        return this == QUEUED || this == PENDING;
    }
}
