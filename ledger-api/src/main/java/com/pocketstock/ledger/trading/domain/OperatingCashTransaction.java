package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 회사 현금 원장 (operating_cash_transactions, DB B 원장, append-only).
 * 복식부기의 상대계정 — 유저 예수금 leg(deposit_transactions)의 반대 부호 짝.
 * 잔액은 행마다 balance_after 스냅샷, 현재잔액은 operating_cash_balances(통화당 1행)가 보유.
 * tx_type: BUY(유저 매수 → 회사 현금 수취,+) / SELL(유저 매도 → 회사 현금 지급,−).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatingCashTransaction {

    private Long id;
    private String currency;
    private String txType;
    private BigDecimal amount;        // +수취 / −지급
    private BigDecimal balanceAfter;
    private String refType;          // order | allocation | batch
    private Long refId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
