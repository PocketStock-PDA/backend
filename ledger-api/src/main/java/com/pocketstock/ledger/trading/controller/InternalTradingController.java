package com.pocketstock.ledger.trading.controller;

import com.pocketstock.ledger.trading.service.PortfolioValuationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * core→ledger 내부 호출 전용(매매 도메인). 인증은 @CurrentUserId 대신 파라미터 userId — 다른 /internal/* 과 동일 패턴.
 */
@RestController
@RequestMapping("/internal/trading")
@RequiredArgsConstructor
public class InternalTradingController {

    private final PortfolioValuationService portfolioValuationService;

    /** 퍼즐판 실시간 평가금액(보유 × 현재가, KRW). 보유 없으면 0. — 마이페이지 puzzleValuation용. */
    @GetMapping("/puzzle-valuation")
    public BigDecimal getPuzzleValuation(@RequestParam Long userId) {
        return portfolioValuationService.getPuzzleValuationKrw(userId);
    }
}
