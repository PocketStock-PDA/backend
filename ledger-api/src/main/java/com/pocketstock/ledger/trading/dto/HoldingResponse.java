package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 보유 종목 항목. 평가액·수익률은 현재가 합성/배치(daily_valuations) 영역이라 여기선 lean.
 */
public record HoldingResponse(
        String stockCode,
        BigDecimal quantity,
        BigDecimal avgBuyPrice,
        String currency
) {
}
