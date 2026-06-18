package com.pocketstock.ledger.exchange.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.exchange.dto.request.FxAutoSettingRequest;
import com.pocketstock.ledger.exchange.dto.response.ExchangeRateResponse;
import com.pocketstock.ledger.exchange.dto.response.FxAutoSettingResponse;
import com.pocketstock.ledger.exchange.dto.response.FxHistoryResponse;
import com.pocketstock.ledger.exchange.service.ExchangeRateService;
import com.pocketstock.ledger.exchange.service.FxAutoSettingService;
import com.pocketstock.ledger.exchange.service.FxQueryService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 환전 API — 환율 조회·이력·자동환전 설정.
 * (환전 체결 {@code /krw-to-usd}·{@code /usd-to-krw}, 검증 {@code /validate}는
 *  CMA 풀 차감/입금 포트 연동 후 추가.)
 */
@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeRateService rateService;
    private final FxQueryService fxQueryService;
    private final FxAutoSettingService autoSettingService;

    /** 환율 조회 — 기준율 + 매수/매도 적용환율(양방향). */
    @GetMapping("/rate")
    public ApiResponse<ExchangeRateResponse> getRate() {
        return ApiResponse.ok("환율 조회 성공", rateService.getUsdKrwRate());
    }

    /** 환전 이력 조회. */
    @GetMapping("/history")
    public ApiResponse<FxHistoryResponse> getHistory(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok("환전 이력 조회 성공",
                fxQueryService.getHistory(userId, safePage, safeSize));
    }

    /** 자동환전 설정 조회. */
    @GetMapping("/auto-settings")
    public ApiResponse<FxAutoSettingResponse> getAutoSettings(@CurrentUserId Long userId) {
        return ApiResponse.ok("자동환전 설정 조회 성공", autoSettingService.get(userId));
    }

    /** 자동환전 설정 변경(1인 1행 upsert). */
    @PutMapping("/auto-settings")
    public ApiResponse<FxAutoSettingResponse> updateAutoSettings(
            @CurrentUserId Long userId,
            @RequestBody FxAutoSettingRequest request) {
        return ApiResponse.ok("자동환전 설정 완료", autoSettingService.update(userId, request));
    }
}
