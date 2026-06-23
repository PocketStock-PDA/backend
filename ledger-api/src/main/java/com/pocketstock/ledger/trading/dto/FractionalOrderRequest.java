package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 소수점 매수/매도 접수 요청. side는 body로 받는다(엔드포인트 단일화 — 온주와 동일 정책).
 * - orderType=AMOUNT: 금액주문 — {@code amount}(국내 ≥1,000원·천원단위 / 해외 ≥$0.01) 필수
 * - orderType=QUANTITY: 수량매수 — {@code quantity}(소수 주수) 필수 (매수 전용)
 * - orderType=ALL: 전량매도 — 보유 전량 (매도 전용, amount·quantity 무시)
 * - clientOrderId: 멱등키(FIX clOrdID). 따닥 탭·재전송 시 같은 값 → 중복 접수 차단(#90 동형).
 * ※ market은 stockCode→exchange에서 파생(요청에 받지 않음, 온주와 동일).
 */
public record FractionalOrderRequest(
        String clientOrderId, // 멱등키(클라 발급)
        String stockCode,
        String side,          // BUY | SELL
        String orderType,     // AMOUNT | QUANTITY | ALL
        BigDecimal amount,    // orderType=AMOUNT일 때 주문금액
        BigDecimal quantity   // orderType=QUANTITY일 때 주문수량(소수)
) {
}
