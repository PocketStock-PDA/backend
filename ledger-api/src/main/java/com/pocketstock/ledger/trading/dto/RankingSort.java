package com.pocketstock.ledger.trading.dto;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;

/**
 * 종목 순위 정렬 기준. 거래대금·시가총액 모두 LS t1463 한 응답에서 만들 수 있다.
 */
public enum RankingSort {
    TRADE_VALUE,
    MARKET_CAP;

    public static RankingSort fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return TRADE_VALUE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "tradevalue", "trade_value", "value" -> TRADE_VALUE;
            case "marketcap", "market_cap", "cap" -> MARKET_CAP;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 정렬 기준: " + raw);
        };
    }
}
