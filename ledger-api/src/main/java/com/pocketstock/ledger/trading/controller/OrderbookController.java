package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.service.OrderbookService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시세 — 호가 조회(온주 전용). domestic=LS t8450(통합), overseas=KIS 현재가호가(HHDFS76200100).
 * 해외는 WS 호가와 동일한 ForeignQuoteResponse로 반환(스냅샷→WS 갱신 정렬).
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class OrderbookController {

    private final OrderbookService orderbookService;

    /** 호가 조회 — market=domestic(LS t8450) / overseas(KIS HHDFS76200100) */
    @GetMapping("/stocks/{stockCode}/orderbook")
    public ApiResponse<?> getOrderbook(
            @CurrentUserId Long userId,
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "domestic") String market) {

        if ("domestic".equalsIgnoreCase(market)) {
            return ApiResponse.ok("[국내] 호가 조회 성공",
                    orderbookService.getDomesticOrderbook(userId, stockCode));
        }
        if ("overseas".equalsIgnoreCase(market)) {
            return ApiResponse.ok("[해외] 호가 조회 성공",
                    orderbookService.getOverseasOrderbook(userId, stockCode));
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 market: " + market);
    }
}
