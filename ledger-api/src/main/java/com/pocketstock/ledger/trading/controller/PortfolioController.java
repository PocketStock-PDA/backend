package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.PortfolioSummaryResponse;
import com.pocketstock.ledger.trading.service.PortfolioSummaryService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포트폴리오 요약 — 전체/국내/해외 평가·수익률 + 종목별 내역. 화면 상단 총합과 보유 카드의 단일 소스.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioSummaryService portfolioSummaryService;

    /** 포트폴리오 요약(전체/국내/해외 집계 + 종목별 평가·수익률). 현재가 스냅샷·현재 환율 환산. */
    @GetMapping("/portfolio/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(@CurrentUserId Long userId) {
        return ApiResponse.ok("포트폴리오 요약 조회 성공", portfolioSummaryService.getSummary(userId));
    }
}
