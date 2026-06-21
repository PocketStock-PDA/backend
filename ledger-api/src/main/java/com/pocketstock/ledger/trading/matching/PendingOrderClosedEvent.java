package com.pocketstock.ledger.trading.matching;

/**
 * 온주 PENDING 종료(사용자 취소)를 알리는 도메인 이벤트.
 * {@code WholeOrderService.cancelOrder}가 취소 트랜잭션 안에서 발행, {@link WholeOrderMatchingEngine}이
 * 커밋 후 인덱스에서 제거하고 그 종목 PENDING이 0건이면 호가 구독을 끈다.
 * (체결로 인한 종료는 엔진이 직접 처리하므로 이 이벤트를 쓰지 않는다.)
 */
public record PendingOrderClosedEvent(
        Long orderId,
        String stockCode,
        String exchange) {
}
