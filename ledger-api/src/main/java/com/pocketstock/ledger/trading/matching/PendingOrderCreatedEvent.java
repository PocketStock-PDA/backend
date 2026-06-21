package com.pocketstock.ledger.trading.matching;

import java.math.BigDecimal;

/**
 * 온주 지정가 미체결 → PENDING 진입을 알리는 도메인 이벤트.
 * {@code WholeOrderService}가 주문 트랜잭션 안에서 발행하고, {@link WholeOrderMatchingEngine}이
 * 커밋 후({@code AFTER_COMMIT}) 인덱스에 등록한다 — 롤백된 PENDING이 인덱스를 더럽히지 않게.
 */
public record PendingOrderCreatedEvent(
        Long orderId,
        Long userId,
        Long accountId,
        String stockCode,
        String exchange,
        String side,
        BigDecimal limitPrice,
        BigDecimal quantity,
        String currency) {
}
