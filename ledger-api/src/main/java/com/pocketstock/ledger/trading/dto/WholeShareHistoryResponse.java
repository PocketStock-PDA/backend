package com.pocketstock.ledger.trading.dto;

import java.time.LocalDateTime;

/** 온주 전환내역 항목 — 어느 종목을 몇 주 전환했는지. */
public record WholeShareHistoryResponse(
        String stockCode,
        String stockName,
        int wholeQty,
        LocalDateTime convertedAt
) {
}
