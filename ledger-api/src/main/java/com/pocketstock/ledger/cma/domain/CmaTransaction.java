package com.pocketstock.ledger.cma.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CmaTransaction {

    private Long id;
    private Long userId;
    private Long cmaAccountId;
    private String currency;
    // tx_type 정식 어휘(단일 출처) — 입금(+): COLLECT, INTEREST, BANK_IN, DORMANT, SAVINGS, SELL_RETURN, FX_IN
    //                                 출금(-): BUY_TRANSFER, FX_OUT / 정정: REVERT
    // FX_IN/FX_OUT은 환전 CmaFundsPort 계약(ref_type='FX_TX', ref_id=fx_transactions.id, 스왑당 2줄)을 따른다.
    private String txType;
    private String sourceType;      // ACCOUNT_CHANGE / CARD_ROUNDUP / POINT / null

    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String refType;
    private Long refId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
