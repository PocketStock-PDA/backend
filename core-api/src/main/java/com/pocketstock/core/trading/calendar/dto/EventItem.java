package com.pocketstock.core.trading.calendar.dto;

import java.time.LocalDate;

public record EventItem(
        String stockCode,
        String eventType,
        LocalDate eventDate,
        String title,
        String detail
) {}
