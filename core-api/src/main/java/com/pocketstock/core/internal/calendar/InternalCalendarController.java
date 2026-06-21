package com.pocketstock.core.internal.calendar;

import com.pocketstock.core.internal.calendar.dto.StockEventUpsertRequest;
import com.pocketstock.core.trading.calendar.mapper.CalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/calendar")
@RequiredArgsConstructor
public class InternalCalendarController {

    private final CalendarMapper calendarMapper;

    @PostMapping("/stock-events")
    @Transactional
    public void upsertStockEvents(@RequestBody List<StockEventUpsertRequest> events) {
        for (StockEventUpsertRequest req : events) {
            calendarMapper.upsertEvent(
                    req.stockCode(), req.eventType(), req.eventDate(), req.title(), req.detail());
        }
    }
}
