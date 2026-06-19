package com.pocketstock.ledger.cma.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.cma.dto.request.CollectionSettingRequest;
import com.pocketstock.ledger.cma.dto.response.CmaAccountResponse;
import com.pocketstock.ledger.cma.dto.response.CmaBalanceResponse;
import com.pocketstock.ledger.cma.dto.response.CmaHomeResponse;
import com.pocketstock.ledger.cma.dto.response.CmaTransactionResponse;
import com.pocketstock.ledger.cma.service.CmaAccountService;
import com.pocketstock.ledger.cma.service.CmaCollectService;
import com.pocketstock.ledger.cma.service.CmaQueryService;
import com.pocketstock.user.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cma")
@RequiredArgsConstructor
public class CmaController {

    private final CmaQueryService queryService;
    private final CmaCollectService collectService;
    private final CmaAccountService accountService;

    /** CMA 계좌 개설(멱등) — 온보딩 마지막 단계. 이미 있으면 기존 계좌 반환. */
    @PostMapping("/account")
    public ApiResponse<CmaAccountResponse> openAccount(@CurrentUserId Long userId) {
        return ApiResponse.ok("CMA 계좌 개설 성공", accountService.openOrGet(userId));
    }

    @GetMapping("/home")
    public ApiResponse<CmaHomeResponse> getHome(@CurrentUserId Long userId) {
        return ApiResponse.ok("홈 대시보드 조회 성공", queryService.getHome(userId));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<CmaTransactionResponse>> getTransactions(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String txType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok("계좌내역 조회 성공",
                queryService.getTransactions(userId, txType, from, to, safePage, safeSize));
    }

    @GetMapping("/balance")
    public ApiResponse<CmaBalanceResponse> getBalance(@CurrentUserId Long userId) {
        return ApiResponse.ok("CMA 잔액 조회 성공", queryService.getBalance(userId));
    }

    @GetMapping("/collect/history")
    public ApiResponse<List<CmaTransactionResponse>> getCollectHistory(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok("적립 이력 조회 성공",
                queryService.getCollectHistory(userId, safePage, safeSize));
    }

    @GetMapping("/transfers")
    public ApiResponse<List<CmaTransactionResponse>> getTransfers(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok("자금 이동 이력 조회 성공",
                queryService.getTransfers(userId, safePage, safeSize));
    }

    @PutMapping("/collect/settings")
    public ApiResponse<Void> updateSettings(
            @CurrentUserId Long userId,
            @RequestBody @Valid CollectionSettingRequest request) {
        collectService.updateSettings(userId, request);
        return ApiResponse.ok("적립 소스 설정 완료", null);
    }
}
