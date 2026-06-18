package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.StockPriceResponse;
import com.pocketstock.ledger.trading.service.StockPriceService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시세 — 현재가 조회. domestic=LS t1102, overseas=KIS 현재가상세(HHDFS76200200).
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class StockPriceController {

    private final StockPriceService stockPriceService;

    /** 현재가 조회 — market=domestic(LS t1102) / overseas(KIS HHDFS76200200) */
    @GetMapping("/stocks/{stockCode}/price")
    public ApiResponse<StockPriceResponse> getPrice(
            @CurrentUserId Long userId,
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "domestic") String market) {

        if ("domestic".equalsIgnoreCase(market)) {
            return ApiResponse.ok("[국내] 현재가 조회 성공",
                    stockPriceService.getDomesticPrice(userId, stockCode));
        }
        if ("overseas".equalsIgnoreCase(market)) {
            return ApiResponse.ok("[해외] 현재가 조회 성공",
                    stockPriceService.getOverseasPrice(userId, stockCode));
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 market: " + market);
    }
}
