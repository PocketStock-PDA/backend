package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockEventUpsertRequest(
        String stockCode,
        String eventType,
        LocalDate eventDate,
        String title,
        String detail,
        BigDecimal amount   // 주당 현금배당금(KRW): 국내=DIVIDEND_PAY, 해외=DIVIDEND_EX. 그 외 null
) {}
