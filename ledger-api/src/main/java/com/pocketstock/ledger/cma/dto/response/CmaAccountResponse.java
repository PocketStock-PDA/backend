package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CMA 계좌 개설 응답 — 계좌번호(복호화), 개설일시, 통화별 지갑 잔액·금리.
 */
public record CmaAccountResponse(
        String cmaAccountNo,
        LocalDateTime openedAt,
        List<BalanceItem> balances
) {
    public record BalanceItem(
            String currency,
            BigDecimal balance,
            BigDecimal interestRate
    ) {
    }
}
