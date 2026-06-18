package com.pocketstock.ledger.exchange.dto.request;

import java.math.BigDecimal;

/**
 * 원화 → 달러 환전 체결({@code POST /api/exchange/krw-to-usd}) 요청.
 * {@code krwAmount}는 차감할 원화(KRW), {@code accountPassword}는 계좌 비밀번호(CMA 검증).
 */
public record KrwToUsdRequest(
        BigDecimal krwAmount,
        String accountPassword
) {
}
