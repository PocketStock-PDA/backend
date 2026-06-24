package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.DailyValuationResponse;
import com.pocketstock.ledger.trading.service.DailyValuationService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 일별 평가·수익률 추이 조회(차트용) — daily_valuations(BATCH-002) 스냅샷 기반. 종가 native, 환차손익 제외.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class ValuationController {

    private final DailyValuationService dailyValuationService;

    /** 종목 수익률·평가 추이(일별, eval_date asc). from/to 생략 시 최근 90일. */
    @GetMapping("/valuations/{stockCode}")
    public ApiResponse<List<DailyValuationResponse>> trend(
            @CurrentUserId Long userId,
            @PathVariable String stockCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok("수익률 추이 조회 성공", dailyValuationService.getTrend(userId, stockCode, from, to));
    }
}
