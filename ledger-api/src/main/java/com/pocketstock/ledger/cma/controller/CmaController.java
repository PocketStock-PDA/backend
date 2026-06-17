package com.pocketstock.ledger.cma.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.cma.dto.response.CmaHomeResponse;
import com.pocketstock.ledger.cma.dto.response.CmaTransactionResponse;
import com.pocketstock.ledger.cma.service.CmaQueryService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cma")
@RequiredArgsConstructor
public class CmaController {

    private final CmaQueryService queryService;

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
}
