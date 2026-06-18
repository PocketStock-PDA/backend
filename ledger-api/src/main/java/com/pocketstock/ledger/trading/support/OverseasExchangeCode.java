package com.pocketstock.ledger.trading.support;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.TradableStock;

/**
 * 해외 종목의 KIS 거래소코드(EXCD) 도출. 현재가·호가 등 KIS REST 호출의 시장 구분에 쓴다.
 * rt_symbol 앞 3자리 우선(예 NASAAPL→NAS), 없으면 exchange 매핑. (도메인은 순수 POJO라 분리)
 */
public final class OverseasExchangeCode {

    private OverseasExchangeCode() {
    }

    /** {@link TradableStock} → KIS EXCD(NAS/NYS/AMS). 매핑 불가 시 INVALID_INPUT. */
    public static String of(TradableStock stock) {
        String rt = stock.getRtSymbol();
        if (rt != null && rt.length() >= 3) {
            return rt.substring(0, 3);
        }
        return switch (stock.getExchange()) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "해외 거래소 매핑 불가: " + stock.getExchange());
        };
    }
}
