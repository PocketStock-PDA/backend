package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 예수금/출금가능/주문가능. 최상위 3필드는 <b>국내(KRW)</b> — 기존 호환 유지(프론트 단일 KRW 화면).
 * {@code balances}는 위탁계좌 시장별 분해(국내 KRW · 해외 USD, #137) — 계좌가 있는 시장만 포함.
 * deposit = 총 예수금(balance), withdrawable·orderable = balance − held(미체결 매수 hold 제외, M2).
 * ※ 출금보류(미결제 차감)는 후속 — 현재 출금가능 = 주문가능.
 */
public record DepositResponse(
        BigDecimal deposit,      // 국내 KRW(하위호환)
        BigDecimal withdrawable,
        BigDecimal orderable,
        List<Balance> balances   // 시장별 분해 [국내 KRW, 해외 USD]
) {
    /** 위탁계좌 1개의 통화별 예수금. */
    public record Balance(String market, String currency,
                          BigDecimal deposit, BigDecimal withdrawable, BigDecimal orderable) {
    }
}
