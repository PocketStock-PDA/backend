package com.pocketstock.ledger.trading.dto;

import com.pocketstock.ledger.trading.domain.AutoInvestStock;

import java.math.BigDecimal;

/**
 * 자동모으기 종목 1건 응답 (목록·단건 공용).
 */
public record AutoInvestResponse(
        Long id,
        String stockCode,
        String stockName,
        String market,
        String period,
        Integer periodDay,
        String amountType,
        BigDecimal buyAmount,
        BigDecimal buyQuantity,
        String currency,
        Boolean isActive
) {
    public static AutoInvestResponse from(AutoInvestStock s) {
        return new AutoInvestResponse(s.getId(), s.getStockCode(), s.getStockName(), s.getMarket(),
                s.getPeriod(), s.getPeriodDay(), s.getAmountType(), s.getBuyAmount(), s.getBuyQuantity(),
                s.getCurrency(), s.getIsActive());
    }
}
