package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.WholeShareConvertRequest;
import com.pocketstock.ledger.trading.dto.WholeShareConvertResponse;
import com.pocketstock.ledger.trading.dto.WholeShareHistoryResponse;
import com.pocketstock.ledger.trading.service.WholeShareService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 온주 전환 — 소수점(신탁) 보유 정수부를 사용자가 버튼으로 온주(직접소유) 전환 + 전환내역 조회 (FRAC-010 #157).
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class WholeShareController {

    private final WholeShareService wholeShareService;

    /** 온주 전환 실행 — 그 종목 소수의 정수부를 온주로 굳힘(1주 미만이면 거부). */
    @PostMapping("/whole-shares")
    public ApiResponse<WholeShareConvertResponse> convert(@CurrentUserId Long userId,
                                                          @RequestBody WholeShareConvertRequest request) {
        return ApiResponse.ok("온주 전환 성공", wholeShareService.convert(userId, request.stockCode()));
    }

    /** 온주 전환내역 조회. */
    @GetMapping("/whole-shares")
    public ApiResponse<List<WholeShareHistoryResponse>> history(@CurrentUserId Long userId) {
        return ApiResponse.ok("온주 전환내역 조회 성공", wholeShareService.getHistory(userId));
    }
}
