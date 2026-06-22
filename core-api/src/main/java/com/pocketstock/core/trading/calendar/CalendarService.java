package com.pocketstock.core.trading.calendar;

import com.pocketstock.core.trading.calendar.dto.*;
import com.pocketstock.core.trading.calendar.mapper.CalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarMapper calendarMapper;

    public TradingCalendarResponse getMonthlyCalendar(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<CalendarEventRow> rows = calendarMapper.findEventsByDateRange(userId, from, to);

        Map<String, List<CalendarEventRow>> byDate = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getEventDate().format(DateTimeFormatter.ISO_LOCAL_DATE)));

        List<CalendarDayEntry> days = byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new CalendarDayEntry(
                        e.getKey(),
                        e.getValue().stream()
                                .map(r -> new CalendarEventSummary(r.getStockCode(), r.getEventType(), r.getTitle()))
                                .toList()
                ))
                .toList();

        return new TradingCalendarResponse(year, month, days);
    }

    public CalendarEventsResponse getMonthlyEvents(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to   = ym.atEndOfMonth();

        List<EventItem> events = calendarMapper
                .findEventsByDateRange(userId, from, to).stream()
                .map(r -> new EventItem(r.getStockCode(), r.getEventType(), r.getEventDate(), r.getTitle(), r.getDetail()))
                .toList();

        return new CalendarEventsResponse(events);
    }
}
