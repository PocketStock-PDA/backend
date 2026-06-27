package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.DividendPayoutResponse;
import com.pocketstock.ledger.trading.dto.DividendReinvestRequest;
import com.pocketstock.ledger.trading.dto.DividendReinvestResponse;
import com.pocketstock.ledger.trading.service.DividendPayoutService;
import com.pocketstock.ledger.trading.service.DividendReinvestService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 배당 자동 재투자(DRIP) — 종목별 ON/OFF 토글 + 배당 지급/재투자 내역.
 * ON = 배당 지급 시 받은 배당으로 같은 종목 재매수(소액은 CMA 잔돈 충당). OFF = CMA 현금 수령.
 * 실제 지급·재투자는 {@code DividendPayoutScheduler}가 지급일 09:00 KST에 집행.
 */
@RestController
@RequestMapping("/api/trading/dividend-reinvest")
@RequiredArgsConstructor
public class DividendReinvestController {

    private final DividendReinvestService dividendReinvestService;
    private final DividendPayoutService dividendPayoutService;

    /** DRIP 토글 설정(종목별 ON/OFF). */
    @PutMapping
    public ApiResponse<DividendReinvestResponse> setEnabled(@CurrentUserId Long userId,
                                                            @RequestBody DividendReinvestRequest request) {
        return ApiResponse.ok("배당 재투자 설정 성공", dividendReinvestService.setEnabled(userId, request));
    }

    /** 내 DRIP 설정 목록(종목별 ON/OFF). */
    @GetMapping
    public ApiResponse<List<DividendReinvestResponse>> list(@CurrentUserId Long userId) {
        return ApiResponse.ok("배당 재투자 설정 조회 성공", dividendReinvestService.list(userId));
    }

    /** 배당 지급/재투자 내역(자산관리 배당 내역). */
    @GetMapping("/history")
    public ApiResponse<List<DividendPayoutResponse>> history(@CurrentUserId Long userId) {
        return ApiResponse.ok("배당 내역 조회 성공", dividendPayoutService.history(userId));
    }
}
