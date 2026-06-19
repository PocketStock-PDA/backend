package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.WelcomeRewardCandidateResponse;
import com.pocketstock.ledger.trading.dto.WelcomeRewardClaimRequest;
import com.pocketstock.ledger.trading.dto.WelcomeRewardResponse;
import com.pocketstock.ledger.trading.service.WelcomeRewardService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 웰컴 보상 — 후보 조회 / 선택·지급 / 내역.
 * 온보딩(계좌개설+연동) 완료 후 첫 주식 선물(1회성).
 */
@RestController
@RequestMapping("/api/trading/rewards")
@RequiredArgsConstructor
public class RewardController {

    private final WelcomeRewardService welcomeRewardService;

    /** 웰컴 보상 후보 종목 조회 (국내 거래대금 1·2위 + 해외 1·2위) */
    @GetMapping("/welcome/candidates")
    public ApiResponse<List<WelcomeRewardCandidateResponse>> getWelcomeCandidates(@CurrentUserId Long userId) {
        return ApiResponse.ok("웰컴 보상 후보 조회 성공", welcomeRewardService.getCandidates(userId));
    }

    /** 웰컴 보상 지급 (후보 중 1종목 선택 → 1,000원어치 소수점 지급) */
    @PostMapping("/welcome")
    public ApiResponse<WelcomeRewardResponse> claimWelcome(@CurrentUserId Long userId,
                                                           @RequestBody WelcomeRewardClaimRequest request) {
        return ApiResponse.ok("웰컴 보상 지급 성공", welcomeRewardService.claim(userId, request.stockCode()));
    }

    /** 보상 지급 내역 조회 */
    @GetMapping
    public ApiResponse<List<WelcomeRewardResponse>> getHistory(@CurrentUserId Long userId) {
        return ApiResponse.ok("보상 내역 조회 성공", welcomeRewardService.getHistory(userId));
    }
}
