package com.pocketstock.core.point.dto;

/**
 * 출석체크(적립) 응답. POST /api/points/attendance
 *
 * @param awarded      이번 출석으로 적립된 포인트(정상 출석 시 10, 중복 출석 시 0)
 * @param streak       출석 처리 후 연속 일수
 * @param balanceAfter 적립 후 마이신한포인트 잔액
 */
public record AttendanceCheckResponse(
        int awarded,
        int streak,
        long balanceAfter
) {
}
