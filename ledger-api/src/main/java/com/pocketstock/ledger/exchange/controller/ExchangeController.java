package com.pocketstock.ledger.exchange.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.exchange.dto.request.FxAutoSettingRequest;
import com.pocketstock.ledger.exchange.dto.request.KrwToUsdRequest;
import com.pocketstock.ledger.exchange.dto.request.UsdToKrwRequest;
import com.pocketstock.ledger.exchange.FxDirection;
import com.pocketstock.ledger.exchange.dto.response.ExchangeRateResponse;
import com.pocketstock.ledger.exchange.dto.response.ExchangeValidateResponse;
import com.pocketstock.ledger.exchange.dto.response.FxAutoSettingResponse;
import com.pocketstock.ledger.exchange.dto.response.FxHistoryResponse;
import com.pocketstock.ledger.exchange.dto.response.KrwToUsdResponse;
import com.pocketstock.ledger.exchange.dto.response.UsdToKrwResponse;
import com.pocketstock.ledger.exchange.service.ExchangeRateService;
import com.pocketstock.ledger.exchange.service.ExchangeSettleService;
import com.pocketstock.ledger.exchange.service.FxAutoSettingService;
import com.pocketstock.ledger.exchange.service.FxQueryService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 환전 API — 환율 조회·검증·이력·자동환전 설정·수동 환전 체결.
 */
@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeRateService rateService;
    private final FxQueryService fxQueryService;
    private final FxAutoSettingService autoSettingService;
    private final ExchangeSettleService settleService;

    /** 환율 조회 — 기준율 + 매수/매도 적용환율(양방향). */
    @GetMapping("/rate")
    public ApiResponse<ExchangeRateResponse> getRate() {
        return ApiResponse.ok("환율 조회 성공", rateService.getUsdKrwRate());
    }

    /**
     * 환전 가능여부·가능금액 검증 — 체결 전 읽기 전용 dry-run.
     * {@code direction}으로 양방향 공용, {@code amount} 생략 시 환율·잔액·최대금액만 반환.
     */
    @GetMapping("/validate")
    public ApiResponse<ExchangeValidateResponse> validate(
            @CurrentUserId Long userId,
            @RequestParam String direction,
            @RequestParam(required = false) BigDecimal amount) {
        return ApiResponse.ok("환전 검증 완료",
                fxQueryService.validate(userId, FxDirection.from(direction), amount));
    }

    /** 원화 → 달러 환전 체결. */
    @PostMapping("/krw-to-usd")
    public ApiResponse<KrwToUsdResponse> krwToUsd(
            @CurrentUserId Long userId,
            @RequestBody KrwToUsdRequest request) {
        return ApiResponse.ok("원화 → 달러 환전 성공", settleService.krwToUsd(userId, request));
    }

    /** 달러 → 원화 환전 체결. */
    @PostMapping("/usd-to-krw")
    public ApiResponse<UsdToKrwResponse> usdToKrw(
            @CurrentUserId Long userId,
            @RequestBody UsdToKrwRequest request) {
        return ApiResponse.ok("달러 → 원화 환전 성공", settleService.usdToKrw(userId, request));
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
