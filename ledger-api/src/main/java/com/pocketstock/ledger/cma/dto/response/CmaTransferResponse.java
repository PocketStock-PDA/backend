package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;

/**
 * CMA 풀 → 위탁 예수금 자금이동 응답.
 * {@code cmaBalanceAfter}는 이체 후 CMA 통화풀 잔액, {@code depositBalanceAfter}는 충전 후 위탁 예수금 잔액.
 */
public record CmaTransferResponse(
        String market,                    // DOMESTIC | OVERSEAS
        String currency,                  // KRW | USD (market 파생)
        BigDecimal transferAmount,        // 이체 금액
        BigDecimal cmaBalanceAfter,       // 이체 후 CMA 풀 잔액
        BigDecimal depositBalanceAfter    // 충전 후 위탁 예수금 잔액
) {
}
