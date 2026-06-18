package com.pocketstock.ledger.exchange.dto.response;

import java.math.BigDecimal;

/**
 * 환율 조회({@code GET /api/exchange/rate}) 응답 — 양방향 UI 전제로
 * 기준율 + 적용환율(매수/매도)을 함께 반환한다. 환산 방향은 클라가 곱/나누기로 처리:
 * <pre>
 *   KRW → USD :  krw ÷ buyRate
 *   USD → KRW :  usd × sellRate
 * </pre>
 *
 * <p>비용(스프레드·우대)은 buy/sellRate에 내재 — 별도 수수료 없음(backend#54).
 * {@code updatedAt}으로 staleness(장 마감/주말 last-known) 판단.
 */
public record ExchangeRateResponse(
        String baseCurrency,       // USD
        String targetCurrency,     // KRW
        BigDecimal baseRate,       // 매매기준율(LS CUR 체결가)
        BigDecimal buyRate,        // 매수 적용환율(KRW→USD, 고객이 USD 살 때)
        BigDecimal sellRate,       // 매도 적용환율(USD→KRW, 고객이 USD 팔 때)
        BigDecimal preferentialRate, // 적용 우대율(예 0.90)
        BigDecimal change,         // 전일대비(부호 포함)
        String updatedAt           // 환율 갱신 시각
) {
}
