package com.pocketstock.ledger.trading.dto;

import com.pocketstock.ledger.trading.domain.MaturityBuyReservation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 만기 후 배당주 매수 예약 응답 — 목록·생성 공용.
 * {@code stockName}은 tradable_stocks 조인 표시용(엔티티 join 컬럼).
 */
public record MaturityReservationResponse(
        Long id,
        Long linkedBankAccountId,
        LocalDate maturityDate,
        String stockCode,
        String stockName,
        String market,
        String currency,
        BigDecimal buyAmount,
        String status,
        Long orderId,
        String failReason,
        LocalDateTime executedAt,
        LocalDateTime createdAt
) {
    public static MaturityReservationResponse from(MaturityBuyReservation r) {
        return new MaturityReservationResponse(
                r.getId(),
                r.getLinkedBankAccountId(),
                r.getMaturityDate(),
                r.getStockCode(),
                r.getStockName(),
                r.getMarket(),
                r.getCurrency(),
                r.getBuyAmount(),
                r.getStatus(),
                r.getOrderId(),
                r.getFailReason(),
                r.getExecutedAt(),
                r.getCreatedAt()
        );
    }
}
