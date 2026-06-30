package com.pocketstock.core.point.domain;

import java.time.LocalDate;

/**
 * 가장 최근 출석 1건 — checkedToday/streak 산정용.
 * 컬럼 순서는 SELECT(attended_date, streak_after)와 일치시킨다.
 */
public record AttendanceRecord(
        LocalDate attendedDate,
        int streakAfter
) {
}
