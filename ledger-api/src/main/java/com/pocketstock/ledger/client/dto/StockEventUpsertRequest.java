package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockEventUpsertRequest(
        String stockCode,
        String eventType,
        LocalDate eventDate,
        String title,
        String detail,
        BigDecimal amount   // DIVIDEND_PAY: 주당 현금배당금(KRW). 그 외 null
) {}
