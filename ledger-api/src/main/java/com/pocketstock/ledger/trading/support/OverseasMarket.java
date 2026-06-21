package com.pocketstock.ledger.trading.support;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;

/**
 * 해외 거래소 → KIS 시장코드 매핑의 단일 진실원(single source of truth).
 * 종목은 {@code (stock_code, exchange)}만 저장하고, 정규장/주간거래 시장코드는 여기서 파생한다.
 *
 * <p>정규장(야간, D-prefix)과 주간거래(KIS 데이마켓, R-prefix)는 같은 거래소라도 코드가 다르다.
 * 예: 나스닥 정규 {@code NAS} / 주간 {@code BAQ}.
 */
public enum OverseasMarket {
    NASDAQ("NAS", "BAQ"),
    NYSE("NYS", "BAY"),
    AMEX("AMS", "BAA");

    private final String regularCode;   // 정규장 시장코드(EXCD) — D-prefix·REST EXCD
    private final String dayCode;        // 주간거래 시장코드 — R-prefix

    OverseasMarket(String regularCode, String dayCode) {
        this.regularCode = regularCode;
        this.dayCode = dayCode;
    }

    public String regularCode() {
        return regularCode;
    }

    public String dayCode() {
        return dayCode;
    }

    /** exchange 컬럼값(NASDAQ/NYSE/AMEX) → enum. 매핑 불가 시 INVALID_INPUT. */
    public static OverseasMarket fromExchange(String exchange) {
        try {
            return valueOf(exchange);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해외 거래소 매핑 불가: " + exchange);
        }
    }
}
