package com.pocketstock.core.point.dto;

import java.time.LocalDate;

/**
 * 출석 현황 조회 응답. GET /api/points/attendance
 *
 * @param checkedToday    오늘(서버 KST 날짜) 이미 출석했는지
 * @param streak          현재 연속 출석 일수(마지막 출석 기준). 이력 없으면 0
 * @param lastCheckedDate 마지막 출석 일자(KST, YYYY-MM-DD). 없으면 null
 * @param dailyReward     1회 출석 적립 포인트(현재 정책 10)
 */
public record AttendanceStatusResponse(
        boolean checkedToday,
        int streak,
        LocalDate lastCheckedDate,
        int dailyReward
) {
}
