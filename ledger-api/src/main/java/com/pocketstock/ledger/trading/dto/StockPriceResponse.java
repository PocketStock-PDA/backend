package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 국내 현재가 응답. changePrice·changeRate는 sign(전일대비구분) 적용 후 부호 포함.
 * ※ 금액 필드는 BigDecimal(가이드라인 + 해외 소수점 시세 대비). volume은 카운트라 long.
 */
public record StockPriceResponse(
        String stockCode,
        BigDecimal currentPrice,
        BigDecimal changePrice,
        BigDecimal changeRate,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal openPrice,
        long volume,
        String asOf                 // 스냅샷 시각(ISO-8601). 캐시 폴백이면 과거값 → staleness 표시(#128)
) {
}
