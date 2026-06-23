package com.pocketstock.ledger.exchange;

import java.math.BigDecimal;

/**
 * 환산 결과 한 줄 — 방향·적용환율·절사된 수령액. {@link FxQuoteCalculator}가 산출.
 * 검증({@code /validate})과 체결이 같은 값을 쓰게 하는 단일 진실원(미리보기==체결).
 */
public record FxQuote(
        String fromCurrency,      // 차감 통화 (KRW | USD)
        String toCurrency,        // 입금 통화
        BigDecimal appliedRate,   // 방향에 맞는 적용환율(매수 or 매도)
        BigDecimal receiveAmount  // 절사 적용된 수령액 (USD 2자리·KRW 0자리, DOWN)
) {
}
