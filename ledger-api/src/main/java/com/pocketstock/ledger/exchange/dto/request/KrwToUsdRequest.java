package com.pocketstock.ledger.exchange.dto.request;

import java.math.BigDecimal;

/**
 * 원화 → 달러 환전 체결({@code POST /api/exchange/krw-to-usd}) 요청.
 * {@code krwAmount}는 차감할 원화(KRW). 거래 인증은 본문 비밀번호가 아니라 사전 거래 세션(txn-auth)으로 처리한다.
 */
public record KrwToUsdRequest(
        BigDecimal krwAmount
) {
}
