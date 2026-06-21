package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 예수금/출금가능/주문가능 금액(KRW).
 * deposit = 총 예수금(balance), withdrawable·orderable = balance − held(미체결 매수 hold 제외, M2).
 * ※ 출금보류(미결제 차감)는 후속 — 현재 출금가능 = 주문가능.
 */
public record DepositResponse(BigDecimal deposit, BigDecimal withdrawable, BigDecimal orderable) {
}
