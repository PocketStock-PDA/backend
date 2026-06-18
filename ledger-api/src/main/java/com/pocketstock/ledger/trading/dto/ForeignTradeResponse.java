package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 해외주식 실시간 체결가(KIS HDFSCNT0). 미국 지연체결가(무료 0분지연).
 *
 * <p>현재가/등락/시고저/거래량 필드명을 REST 현재가({@link StockPriceResponse})와 동일하게 맞춰,
 * 프론트가 REST 스냅샷으로 첫 렌더 후 이 WS 틱으로 그대로 갱신할 수 있게 한다.
 * {@code symbol/realtimeCode/localTime/lastTradeVolume/tradeStrength}는 WS 전용 추가 필드.
 */
public record ForeignTradeResponse(
        String symbol,             // SYMB
        String realtimeCode,       // RSYM — 토픽 키
        String localTime,          // XHMS 현지시간
        BigDecimal currentPrice,   // LAST 현재가(체결가)
        BigDecimal changePrice,    // DIFF 전일대비(SIGN 부호 적용)
        BigDecimal changeRate,     // RATE 등락율
        BigDecimal openPrice,      // OPEN 시가
        BigDecimal highPrice,      // HIGH 고가
        BigDecimal lowPrice,       // LOW 저가
        long volume,               // TVOL 누적거래량
        long lastTradeVolume,      // EVOL 이번 틱 체결량(WS 전용)
        BigDecimal tradeStrength   // STRN 체결강도(WS 전용)
) {
}
