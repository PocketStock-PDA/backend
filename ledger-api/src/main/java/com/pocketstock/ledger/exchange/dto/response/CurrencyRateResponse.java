package com.pocketstock.ledger.exchange.dto.response;

import java.math.BigDecimal;

/**
 * 실시간 환율(USD/KRW) — LS CUR(현물USD 실시간) 프레임을 매핑해
 * {@code /topic/currency/usd-krw}로 push 한다.
 *
 * <p>{@code exchangeRate}는 체결가(price), {@code change}는 전일대비(절대값)에
 * sign(전일대비구분 4하한·5하락)을 적용해 부호를 포함한다 — REST 환율 조회(CUR)와 동일 규칙.
 * {@code updatedAt}은 LS가 날짜 없이 HHmmss(time)만 주므로 수신 시각으로 서버가 채운다.
 */
public record CurrencyRateResponse(
        String baseCurrency,     // USD
        String targetCurrency,   // KRW
        BigDecimal exchangeRate, // price 체결가(환율)
        BigDecimal change,       // change 전일대비(절대값) → sign 부호 적용
        String updatedAt         // 수신 시각 ISO-8601(밀리초)
) {
}
