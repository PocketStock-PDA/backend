package com.pocketstock.ledger.exchange.dto.request;

import java.math.BigDecimal;

/**
 * 달러 → 원화 환전 체결({@code POST /api/exchange/usd-to-krw}) 요청.
 * {@code usdAmount}는 차감할 달러(USD). 거래 인증은 본문 비밀번호가 아니라 사전 거래 세션(txn-auth)으로 처리한다.
 */
public record UsdToKrwRequest(
        BigDecimal usdAmount
) {
}
