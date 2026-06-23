package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 소수점 매수 split 결과 — 한 주문(예: 13.14주)을 정수부=온주(즉시 호가체결)와 소수부=소수(차수 배치)로
 * 쪼갠 결과를 합쳐 반환한다(FRAC-010 #157). 한 트랜잭션이라 둘 다 성공 or 둘 다 롤백.
 *
 * <p>{@code whole*}=온주분(없으면 null), {@code fractional*}=소수분(없으면 null).
 * 예: 0.1주 → 소수만 / 1.0주 → 온주만 / 13.14주 → 온주 13 + 소수 0.14.
 */
public record SplitOrderResponse(
        String stockCode,
        String side,                    // BUY
        // 온주분(즉시 체결) — 없으면 null
        Long wholeOrderId,
        Long wholeQty,
        BigDecimal wholeFillPrice,
        BigDecimal wholeAmount,
        // 소수분(QUEUED 차수 대기) — 없으면 null
        Long fractionalOrderId,
        Long roundId,
        BigDecimal fractionalEstQty,
        BigDecimal fractionalHeld,
        String fractionalStatus,        // QUEUED
        BigDecimal orderable            // 처리 후 예수금 잔액
) {
}
