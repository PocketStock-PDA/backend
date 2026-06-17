package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예수금 원장 (deposit_transactions, DB B 원장, append-only).
 * 잔액은 누적이 아니라 행마다 balance_after 스냅샷으로 보관 → 최신 행이 현재 잔액.
 * tx_type: IN_TRANSFER(CMA→예수금) / BUY(매수출금,-) / SELL(매도입금,+) / REVERT.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositTransaction {

    private Long id;
    private Long userId;
    private Long accountId;
    private String txType;
    private BigDecimal amount;        // +입금 / −출금
    private String currency;
    private BigDecimal balanceAfter;
    private String refType;          // order | cma_tx ...
    private Long refId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
