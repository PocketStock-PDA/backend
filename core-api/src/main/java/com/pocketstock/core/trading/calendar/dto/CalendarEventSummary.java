package com.pocketstock.core.trading.calendar.dto;

public record CalendarEventSummary(
        String stockCode,
        String eventType,
        String title
) {}
