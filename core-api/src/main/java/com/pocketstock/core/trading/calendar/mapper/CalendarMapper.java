package com.pocketstock.core.trading.calendar.mapper;

import com.pocketstock.core.trading.calendar.dto.CalendarEventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
                     @Param("detail") String detail);
}
