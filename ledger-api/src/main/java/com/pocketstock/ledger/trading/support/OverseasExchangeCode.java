package com.pocketstock.ledger.trading.support;

import com.pocketstock.ledger.trading.domain.TradableStock;

/**
 * 해외 종목의 KIS 거래소코드(EXCD) 도출. 현재가·호가 등 KIS REST 호출의 시장 구분에 쓴다.
 * 거래소값에서 {@link OverseasMarket} 정규장 코드(NAS/NYS/AMS)로 파생한다.
 */
public final class OverseasExchangeCode {

    private OverseasExchangeCode() {
    }

    /** {@link TradableStock} → KIS EXCD(NAS/NYS/AMS). 매핑 불가 시 INVALID_INPUT. */
    public static String of(TradableStock stock) {
        return OverseasMarket.fromExchange(stock.getExchange()).regularCode();
    }
}
