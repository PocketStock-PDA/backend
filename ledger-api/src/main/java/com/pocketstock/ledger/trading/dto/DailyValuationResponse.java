package com.pocketstock.ledger.trading.dto;

import com.pocketstock.ledger.trading.domain.DailyValuation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일별 평가 스냅샷 1건 (수익률 추이 차트용). 종가 기준 native — 환차손익 제외.
 */
public record DailyValuationResponse(
        LocalDate evalDate,
        BigDecimal quantity,
        BigDecimal closePrice,
        BigDecimal evalAmount,
        BigDecimal profitAmount,
        BigDecimal profitRate,
        String currency
) {
    public static DailyValuationResponse from(DailyValuation v) {
        return new DailyValuationResponse(v.getEvalDate(), v.getQuantity(), v.getClosePrice(),
                v.getEvalAmount(), v.getProfitAmount(), v.getProfitRate(), v.getCurrency());
    }
}
