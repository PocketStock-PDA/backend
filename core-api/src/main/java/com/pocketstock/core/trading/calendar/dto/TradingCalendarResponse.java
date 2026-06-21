package com.pocketstock.core.trading.calendar.dto;

import java.util.List;

public record TradingCalendarResponse(
        int year,
        int month,
        List<CalendarDayEntry> days
) {}
