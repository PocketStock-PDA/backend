package com.pocketstock.ledger.exchange.port;

import java.math.BigDecimal;

/**
 * 환전 양다리 체결 후 CMA 풀 잔액 — 체결 응답의 {@code remain*} 채움용.
 */
public record FxLegResult(
        BigDecimal remainFrom,  // 차감된 통화(from) 풀 잔액
        BigDecimal remainTo     // 입금된 통화(to) 풀 잔액
) {
}
