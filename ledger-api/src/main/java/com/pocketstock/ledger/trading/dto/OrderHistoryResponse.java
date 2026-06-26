package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래내역 항목.
 */
public record OrderHistoryResponse(
        Long orderId,
        String stockCode,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal orderAmount,
        BigDecimal price,
        String status,
        String currency,
        LocalDateTime createdAt,
        /** 체결금액 — 소수점은 allocations.gross_amount 합, 온주는 null(프론트가 체결가×수량). 미체결은 null. */
        BigDecimal filledAmount
) {
}
