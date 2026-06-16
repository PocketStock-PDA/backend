package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.AccountStatusResponse;
import com.pocketstock.ledger.trading.dto.DepositResponse;
import com.pocketstock.ledger.trading.dto.OpenAccountRequest;
import com.pocketstock.ledger.trading.dto.OpenAccountResponse;
import com.pocketstock.ledger.trading.service.SecuritiesAccountService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 증권계좌 — 개설 / 상태조회 / 예수금조회.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class SecuritiesAccountController {

    private final SecuritiesAccountService accountService;

    /** 증권계좌 개설 (CMA + 국내·해외 위탁) */
    @PostMapping("/accounts")
    public ApiResponse<OpenAccountResponse> open(@CurrentUserId Long userId,
                                                 @RequestBody OpenAccountRequest request) {
        return ApiResponse.ok("증권계좌 개설 성공", accountService.open(userId, request));
    }

    /** 계좌 상태 조회 */
    @GetMapping("/accounts")
    public ApiResponse<List<AccountStatusResponse>> getAccounts(@CurrentUserId Long userId) {
        return ApiResponse.ok("계좌 상태 조회 성공", accountService.getAccounts(userId));
    }

    /** 예수금/출금가능금액 조회 */
    @GetMapping("/deposit")
    public ApiResponse<DepositResponse> getDeposit(@CurrentUserId Long userId) {
        return ApiResponse.ok("예수금 조회 성공", accountService.getDeposit(userId));
    }
}
