package com.pocketstock.core.trading.calendar;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.trading.calendar.dto.CalendarEventsResponse;
import com.pocketstock.core.trading.calendar.dto.TradingCalendarResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<TradingCalendarResponse>> getMonthlyCalendar(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        if (m < 1 || m > 12) throw new BusinessException(ErrorCode.INVALID_INPUT);

        return ResponseEntity.ok(ApiResponse.ok("증권 캘린더 조회 성공",
                calendarService.getMonthlyCalendar(userId, y, m)));
    }

    @GetMapping("/calendar/events")
    public ResponseEntity<ApiResponse<CalendarEventsResponse>> getUpcomingEvents(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.ok("보유 종목 주요일정 조회 성공",
                calendarService.getUpcomingEvents(userId)));
    }
}
