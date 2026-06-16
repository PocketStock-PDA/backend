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
        BigDecimal price,
        String status,
        LocalDateTime createdAt
) {
}
