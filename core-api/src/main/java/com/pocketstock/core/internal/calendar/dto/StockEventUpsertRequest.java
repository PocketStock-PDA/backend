package com.pocketstock.core.internal.calendar.dto;

import java.time.LocalDate;

public record StockEventUpsertRequest(
        String stockCode,
        String eventType,
        LocalDate eventDate,
        String title,
        String detail
) {}
