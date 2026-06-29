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

    /**
     * 배당락일 DIVIDEND_EX 중 주당배당금(amount)이 실린 일정 — 해외(US) 배당 지급 엔진용.
     * 해외는 정확한 지급일이 없어 PAY 대신 EX에 주당배당금(원화 환산)을 싣는다. 국내 EX는 amount가 없어 제외된다.
     */
    List<DividendPayoutScheduleRow> findDividendExPayouts(@Param("date") LocalDate date);
}
