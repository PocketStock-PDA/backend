package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.StockDetailResponse;
import com.pocketstock.ledger.trading.dto.StockSearchItem;
import com.pocketstock.ledger.trading.service.StockService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 시세 — 종목 검색(자체 종목마스터) / 종목 상세(마스터+현재가 합성).
 * ※ 현재가 단건 조회(t1102)는 {@link StockPriceController} 담당.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** 종목 검색(자체 종목마스터) — 종목명/코드 부분일치 */
    @GetMapping("/stocks/search")
    public ApiResponse<List<StockSearchItem>> search(
            @CurrentUserId Long userId,
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok("종목 검색 성공", stockService.search(userId, keyword, limit));
    }

    /** 종목 상세(마스터 + 현재가 합성) */
    @GetMapping("/stocks/{stockCode}")
    public ApiResponse<StockDetailResponse> getDetail(
            @CurrentUserId Long userId,
            @PathVariable String stockCode) {
        return ApiResponse.ok("종목 상세 조회 성공", stockService.getDetail(userId, stockCode));
    }
}
