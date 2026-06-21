package com.pocketstock.core.trading.calendar.dto;

import java.util.List;

public record CalendarDayEntry(
        String date,
        List<CalendarEventSummary> events
) {}
