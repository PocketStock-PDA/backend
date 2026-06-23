package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 보유 종목 항목. 평가액·수익률은 현재가 합성/배치(daily_valuations) 영역이라 여기선 lean.
 * quantity=총 보유(온주+소수). wholeQty=온주(직접소유·정수매도) / fractionalQty=소수점(신탁·소수매도≤이값, FRAC-010).
 */
public record HoldingResponse(
        String stockCode,
        BigDecimal quantity,
        BigDecimal wholeQty,
        BigDecimal fractionalQty,
        BigDecimal avgBuyPrice,
        String currency
) {
}
