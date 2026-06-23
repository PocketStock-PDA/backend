package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.OrderCancelResponse;
import com.pocketstock.ledger.trading.dto.OrderHistoryResponse;
import com.pocketstock.ledger.trading.dto.WholeOrderRequest;
import com.pocketstock.ledger.trading.dto.WholeOrderResponse;
import com.pocketstock.ledger.trading.service.WholeOrderService;
import com.pocketstock.user.security.CurrentUserId;
import com.pocketstock.user.security.TxnAuthGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 주문 — 온주 매수/매도(호가 기반) / 거래내역. 소수점 매수·매도는 추후.
 */
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class OrderController {

    private final WholeOrderService wholeOrderService;
    private final TxnAuthGuard txnAuthGuard;

    /** 온주 매수/매도 (호가 기반, 자체 시뮬 체결) */
    @PostMapping("/orders/whole")
    public ApiResponse<WholeOrderResponse> placeWhole(@CurrentUserId Long userId,
                                                      @RequestBody WholeOrderRequest request) {
        // 거래 인증(비번) — 매매 진입에서 1회(#174). CMA풀에서 자동충당이 일어나므로 수동이체·환전과 동일 정책.
        txnAuthGuard.requireTxnAuth(userId);
        return ApiResponse.ok("온주 주문 체결 성공", wholeOrderService.placeWholeOrder(userId, request));
    }

    /** 거래내역 조회 */
    @GetMapping("/orders")
    public ApiResponse<List<OrderHistoryResponse>> getOrders(@CurrentUserId Long userId) {
        return ApiResponse.ok("거래내역 조회 성공", wholeOrderService.getOrderHistory(userId));
    }

    /** 주문 취소 — 소수점 QUEUED / 온주 PENDING만 취소(종결 상태는 409). */
    @DeleteMapping("/orders/{orderId}")
    public ApiResponse<OrderCancelResponse> cancel(@CurrentUserId Long userId,
                                                   @PathVariable Long orderId) {
        return ApiResponse.ok("주문 취소 성공", wholeOrderService.cancelOrder(userId, orderId));
    }
}
