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
                                .map(r -> new CalendarEventSummary(r.getStockCode(), normalizeEventType(r.getEventType()), r.getTitle()))
                                .toList()
                ))
                .toList();

        return new TradingCalendarResponse(year, month, days);
    }

    /**
     * 저장된 세부 이벤트 타입을 프론트 표시용으로 정규화한다.
     * 배당은 DB에 {@code DIVIDEND_EX}(배당락)·{@code DIVIDEND_PAY}(지급)로 저장되지만(지급 엔진이 PAY를 따로 조회),
     * 캘린더는 둘 다 {@code DIVIDEND}(배당)로 보여준다. EARNINGS·RECOMMEND는 그대로.
     */
    private static String normalizeEventType(String raw) {
        if (raw != null && raw.startsWith("DIVIDEND")) {
            return "DIVIDEND";
        }
        return raw;
    }

    public CalendarEventsResponse getMonthlyEvents(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to   = ym.atEndOfMonth();

        List<EventItem> events = calendarMapper
                .findEventsByDateRange(userId, from, to).stream()
                .map(r -> new EventItem(r.getStockCode(), normalizeEventType(r.getEventType()), r.getEventDate(), r.getTitle(), r.getDetail()))
                .toList();

        return new CalendarEventsResponse(events);
    }
}
