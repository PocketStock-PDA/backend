package com.pocketstock.ledger.cma.dto.response;

import com.pocketstock.ledger.cma.domain.CmaTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CmaTransactionResponse(
        Long id,
        String txType,
        String sourceType,
        String currency,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime createdAt
) {
    public static CmaTransactionResponse from(CmaTransaction tx) {
        return new CmaTransactionResponse(
                tx.getId(),
                tx.getTxType(),
                tx.getSourceType(),
                tx.getCurrency(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getCreatedAt()
        );
    }
}
