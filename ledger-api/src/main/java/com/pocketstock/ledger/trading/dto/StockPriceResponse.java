package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 국내 현재가 응답. changePrice·changeRate는 sign(전일대비구분) 적용 후 부호 포함.
 */
public record StockPriceResponse(
        String stockCode,
        long currentPrice,
        long changePrice,
        BigDecimal changeRate,
        long highPrice,
        long lowPrice,
        long openPrice,
        long volume
) {
}
