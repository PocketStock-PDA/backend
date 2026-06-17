package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 국내 실시간 체결가(LS US3 통합 체결, 소수점·온주 공용). 해외 {@link ForeignTradeResponse}의 국내 짝.
 *
 * <p>현재가/등락/시고저/거래량 필드명을 REST 현재가({@link StockPriceResponse}, t1102)와 동일하게 맞춰,
 * 프론트가 REST 스냅샷으로 첫 렌더 후 이 WS 틱으로 그대로 갱신할 수 있게 한다.
 * {@code tradeTime/lastTradeVolume/tradeType/tradeStrength}는 WS 전용 추가 필드.
 *
 * <p>가격은 KRX+NXT 통합 체결값(거래소 무관 단일). changePrice·changeRate는
 * sign(전일대비구분 4하한·5하락)을 적용해 부호를 포함한다 — t1102 REST와 동일 규칙.
 */
public record StockTradeResponse(
        String stockCode,          // shcode 단축코드
        String tradeTime,          // chetime 체결시간 HHmmss
        BigDecimal currentPrice,   // price 현재가(체결가)
        BigDecimal changePrice,    // change 전일대비(절대값) → sign 부호 적용
        BigDecimal changeRate,     // drate 등락율(절대값) → sign 부호 적용
        BigDecimal openPrice,      // open 시가
        BigDecimal highPrice,      // high 고가
        BigDecimal lowPrice,       // low 저가
        long volume,               // volume 누적거래량
        long lastTradeVolume,      // cvolume 이번 틱 체결량(WS 전용)
        String tradeType,          // cgubun + 매수 / - 매도 (WS 전용)
        BigDecimal tradeStrength   // cpower 체결강도(WS 전용)
) {
}
