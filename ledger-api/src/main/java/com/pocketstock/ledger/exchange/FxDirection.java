package com.pocketstock.ledger.exchange;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;

/**
 * 환전 방향 — from풀(차감)·to풀(입금) 통화를 한 곳에 묶는다.
 * 체결({@code ExchangeSettleService})·검증({@code /validate})·환산({@link FxQuoteCalculator})이 공유.
 */
public enum FxDirection {

    /** 원화 → 달러: KRW 풀 차감, 매수환율 적용. */
    KRW_TO_USD("KRW", "USD"),
    /** 달러 → 원화: USD 풀 차감, 매도환율 적용. */
    USD_TO_KRW("USD", "KRW");

    private final String from;
    private final String to;

    FxDirection(String from, String to) {
        this.from = from;
        this.to = to;
    }

    /** 차감되는(파는) 통화. */
    public String from() {
        return from;
    }

    /** 입금되는(받는) 통화. */
    public String to() {
        return to;
    }

    /** 쿼리 파라미터 → enum. 미지정/오타는 400. */
    public static FxDirection from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "환전 방향(direction)이 필요합니다. (KRW_TO_USD | USD_TO_KRW)");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "환전 방향(direction)은 KRW_TO_USD 또는 USD_TO_KRW 여야 합니다.");
        }
    }
}
