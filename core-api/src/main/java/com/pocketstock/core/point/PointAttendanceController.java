package com.pocketstock.core.point;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.point.dto.AttendanceCheckResponse;
import com.pocketstock.core.point.dto.AttendanceStatusResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 출석체크 API. 포인트(/points) 화면 최상단 출석체크 카드.
 * 조회(GET)와 적립 실행(POST)을 분리한다.
 */
@RestController
@RequestMapping("/api/points/attendance")
@RequiredArgsConstructor
public class PointAttendanceController {

    private final PointAttendanceService service;

    /** 출석 현황 조회 — checkedToday / streak / lastCheckedDate / dailyReward. */
    @GetMapping
    public ResponseEntity<ApiResponse<AttendanceStatusResponse>> getStatus(
            @CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.ok("출석 현황 조회 성공", service.getStatus(userId)));
    }

    /** 출석체크(적립). 하루 1회, 중복 호출 시 awarded:0으로 멱등 처리. */
    @PostMapping
    public ResponseEntity<ApiResponse<AttendanceCheckResponse>> check(
            @CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.ok("출석체크 성공", service.check(userId)));
    }
}
