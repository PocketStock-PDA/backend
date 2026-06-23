package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.FractionalOrderResponse;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.service.FractionalOrderService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소수점 주문 — 접수 즉시 1분 차수에 QUEUED 편입(비동기, 체결은 배치 집행기). 온주({@code /orders/whole})와 분리.
 * 취소·거래내역은 공용 {@code OrderController}(DELETE /orders/{id}·GET /orders) 사용.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class FractionalOrderController {

    private final FractionalOrderService fractionalOrderService;

    /** 소수점 매수 (AMOUNT 금액 / QUANTITY 수량) — 백엔드가 정수부=온주 즉시체결 / 소수부=소수 차수배치로 split. */
    @PostMapping("/orders/fractional/buy")
    public ApiResponse<SplitOrderResponse> buy(@CurrentUserId Long userId,
                                               @RequestBody FractionalOrderRequest request) {
        return ApiResponse.ok("소수점 매수 접수 성공", fractionalOrderService.placeBuy(userId, request));
    }

    /** 소수점 매도 (AMOUNT 금액 / ALL 전량) */
    @PostMapping("/orders/fractional/sell")
    public ApiResponse<FractionalOrderResponse> sell(@CurrentUserId Long userId,
                                                     @RequestBody FractionalOrderRequest request) {
        return ApiResponse.ok("소수점 매도 접수 성공", fractionalOrderService.placeSell(userId, request));
    }
}
