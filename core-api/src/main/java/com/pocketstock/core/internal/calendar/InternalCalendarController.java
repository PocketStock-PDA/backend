package com.pocketstock.core.internal.calendar;

import com.pocketstock.core.internal.calendar.dto.DividendPayoutScheduleRow;
import com.pocketstock.core.internal.calendar.dto.StockEventUpsertRequest;
import com.pocketstock.core.trading.calendar.mapper.CalendarMapper;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/internal/calendar")
@RequiredArgsConstructor
public class InternalCalendarController {

    private final CalendarMapper calendarMapper;

    @PostMapping("/stock-events")
    @Transactional
    public void upsertStockEvents(@RequestBody @Valid List<StockEventUpsertRequest> events) {
        for (StockEventUpsertRequest req : events) {
            calendarMapper.upsertEvent(
                    req.stockCode(), req.eventType(), req.eventDate(), req.title(), req.detail(), req.amount());
        }
    }

    /**
     * 지급일 배당 일정(배당 지급 엔진용) — 그 날짜 DIVIDEND_PAY 이벤트 중 주당배당금(amount)이 있는 종목만.
     * ledger가 보유자와 조인해 보유수량×주당배당금으로 지급한다.
     */
    @GetMapping("/dividend-payouts")
    public List<DividendPayoutScheduleRow> getDividendPayouts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return calendarMapper.findDividendPayouts(date);
    }
}
