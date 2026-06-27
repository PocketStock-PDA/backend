package com.pocketstock.core.trading.calendar.mapper;

import com.pocketstock.core.internal.calendar.dto.DividendPayoutScheduleRow;
import com.pocketstock.core.trading.calendar.dto.CalendarEventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CalendarMapper {

    List<CalendarEventRow> findEventsByDateRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    void upsertEvent(@Param("stockCode") String stockCode,
                     @Param("eventType") String eventType,
                     @Param("eventDate") LocalDate eventDate,
                     @Param("title") String title,
                     @Param("detail") String detail,
                     @Param("amount") BigDecimal amount);

    /** 지급일 DIVIDEND_PAY 일정(주당배당금 있는 종목만) — 배당 지급 엔진용. */
    List<DividendPayoutScheduleRow> findDividendPayouts(@Param("date") LocalDate date);
}
