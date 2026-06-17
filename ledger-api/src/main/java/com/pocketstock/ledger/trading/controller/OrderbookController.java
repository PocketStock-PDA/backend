package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.OrderbookResponse;
import com.pocketstock.ledger.trading.service.OrderbookService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시세 — 호가 조회(온주 전용). domestic은 LS t8450, overseas(g3106)는 추후.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class OrderbookController {

    private final OrderbookService orderbookService;

    /** [국내] 호가 조회 (t8450) */
    @GetMapping("/stocks/{stockCode}/orderbook")
    public ApiResponse<OrderbookResponse> getOrderbook(
            @CurrentUserId Long userId,
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "domestic") String market) {

        if (!"domestic".equalsIgnoreCase(market)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해외 호가(g3106)는 추후 지원");
        }
        return ApiResponse.ok("[국내] 호가 조회 성공",
                orderbookService.getDomesticOrderbook(userId, stockCode));
    }
}
