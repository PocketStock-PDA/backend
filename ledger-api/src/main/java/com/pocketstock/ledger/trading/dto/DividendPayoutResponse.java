package com.pocketstock.ledger.trading.dto;

import com.pocketstock.ledger.trading.domain.DividendPayout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 배당 지급/재투자 내역 1행 — 자산관리 "배당 내역" 화면용.
 */
public record DividendPayoutResponse(
        Long id,
        String stockCode,
        String stockName,
        LocalDate payDate,
        BigDecimal holdingQty,
        BigDecimal perShare,
        BigDecimal grossAmount,
        String status,
        Long reinvestOrderId,
        BigDecimal reinvestAmount,
        String failReason,
        LocalDateTime createdAt
) {
    public static DividendPayoutResponse from(DividendPayout p) {
        return new DividendPayoutResponse(
                p.getId(),
                p.getStockCode(),
                p.getStockName(),
                p.getPayDate(),
                p.getHoldingQty(),
                p.getPerShare(),
                p.getGrossAmount(),
                p.getStatus(),
                p.getReinvestOrderId(),
                p.getReinvestAmount(),
                p.getFailReason(),
                p.getCreatedAt()
        );
    }
}
