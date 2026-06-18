package com.pocketstock.ledger.exchange.dto.request;

import java.math.BigDecimal;

/**
 * 달러 → 원화 환전 체결({@code POST /api/exchange/usd-to-krw}) 요청.
 * {@code usdAmount}는 차감할 달러(USD), {@code accountPassword}는 계좌 비밀번호(CMA 검증).
 */
public record UsdToKrwRequest(
        BigDecimal usdAmount,
        String accountPassword
) {
}
