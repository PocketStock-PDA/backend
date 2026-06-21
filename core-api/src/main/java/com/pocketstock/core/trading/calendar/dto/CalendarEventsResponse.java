package com.pocketstock.core.trading.calendar.dto;

import java.util.List;

public record CalendarEventsResponse(
        List<EventItem> events
) {}
