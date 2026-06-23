package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.service.FractionalOrderService;
import com.pocketstock.user.security.CurrentUserId;
import com.pocketstock.user.security.TxnAuthGuard;
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
    private final TxnAuthGuard txnAuthGuard;

    /**
     * 소수점 매수/매도 — side(body)로 구분(온주 /orders/whole과 동일 정책으로 통일).
     * 백엔드가 정수부=온주 즉시체결 / 소수부=소수 차수배치로 split. AMOUNT 금액 / QUANTITY 수량 / ALL 전량(매도).
     */
    @PostMapping("/orders/fractional")
    public ApiResponse<SplitOrderResponse> place(@CurrentUserId Long userId,
                                                 @RequestBody FractionalOrderRequest request) {
        // 거래 인증(비번) — 매매 진입에서 1회(#174). 정수부 재사용(placeWholeOrder)·소수분 충당 모두 이 인증으로 커버.
        txnAuthGuard.requireTxnAuth(userId);
        return ApiResponse.ok("소수점 주문 접수 성공", fractionalOrderService.place(userId, request));
    }
}
