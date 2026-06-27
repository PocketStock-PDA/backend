package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/** 주문별 체결금액 합(소수점 배분 allocations.gross_amount 합산) — 거래내역 금액 표시용 projection. */
public record OrderFilledAmount(Long orderId, BigDecimal amount) {
}
