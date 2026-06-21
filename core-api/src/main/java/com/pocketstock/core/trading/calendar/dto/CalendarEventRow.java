package com.pocketstock.core.trading.calendar.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalendarEventRow {
    private LocalDate eventDate;
    private String stockCode;
    private String eventType;
    private String title;
    private String detail;
}
