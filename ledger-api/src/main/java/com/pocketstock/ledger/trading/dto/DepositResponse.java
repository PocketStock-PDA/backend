package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 예수금/출금가능/주문가능 금액(KRW).
 * ※ 현 단계: 자금 유입·미체결 주문 흐름 미구현 → withdrawable·orderable = deposit(예수금 잔액).
 *   추후 출금보류·미체결 증거금 반영 시 분리.
 */
public record DepositResponse(BigDecimal deposit, BigDecimal withdrawable, BigDecimal orderable) {
}
