package com.pocketstock.ledger.client.dto;

import java.time.LocalDate;

public record StockEventUpsertRequest(
        String stockCode,
        String eventType,
        LocalDate eventDate,
        String title,
        String detail
) {}
