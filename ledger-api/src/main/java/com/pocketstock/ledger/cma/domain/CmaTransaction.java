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
    private String txType;          // COLLECT / WITHDRAW / INTEREST / TRANSFER_OUT
    private String sourceType;      // ACCOUNT_CHANGE / CARD_ROUNDUP / POINT / null
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String refType;
    private Long refId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
