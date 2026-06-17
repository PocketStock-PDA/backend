package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.HoldingResponse;
import com.pocketstock.ledger.trading.service.HoldingService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 보유 종목·잔고 조회.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    /** 보유종목·잔고 조회 */
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> getHoldings(@CurrentUserId Long userId) {
        return ApiResponse.ok("보유종목 조회 성공", holdingService.getHoldings(userId));
    }
}
