package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.StockDetailResponse;
import com.pocketstock.ledger.trading.dto.StockRankingItem;
import com.pocketstock.ledger.trading.dto.StockSearchItem;
import com.pocketstock.ledger.trading.service.StockRankingService;
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
    private final StockRankingService stockRankingService;

    /**
     * 국내 종목 순위 — LS t1463(거래대금·시총·ETF제외) ∩ 자체 종목마스터 재랭킹.
     * sort=tradevalue|marketcap. 거래대금·시총 둘 다 채워 응답.
     */
    @GetMapping("/stocks/rankings/domestic")
    public ApiResponse<List<StockRankingItem>> domesticRankings(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "tradevalue") String sort) {
        return ApiResponse.ok("국내 종목 순위 조회 성공", stockRankingService.getDomesticRanking(userId, sort));
    }

    /**
     * 해외 종목 순위 — KIS(NAS/NYS 머지·개별주만) ∩ 자체 종목마스터 재랭킹.
     * sort=tradevalue|marketcap. KIS는 정렬 지표별 TR이 갈려 정렬한 지표만 채워짐(반대쪽 null), 값 단위 USD.
     */
    @GetMapping("/stocks/rankings/overseas")
    public ApiResponse<List<StockRankingItem>> overseasRankings(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "tradevalue") String sort) {
        return ApiResponse.ok("해외 종목 순위 조회 성공", stockRankingService.getOverseasRanking(userId, sort));
    }

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
