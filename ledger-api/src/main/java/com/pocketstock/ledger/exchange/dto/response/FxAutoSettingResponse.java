package com.pocketstock.ledger.exchange.dto.response;

import com.pocketstock.ledger.exchange.domain.FxAutoSetting;

import java.math.BigDecimal;

/**
 * 자동환전 설정 조회/변경 응답.
 */
public record FxAutoSettingResponse(
        boolean autoEnabled,
        boolean useDollarFirst,
        BigDecimal maxAmountPerTx,
        String residualHandling
) {

    public static FxAutoSettingResponse from(FxAutoSetting s) {
        return new FxAutoSettingResponse(
                Boolean.TRUE.equals(s.getIsAutoEnabled()),
                Boolean.TRUE.equals(s.getUseDollarFirst()),
                s.getMaxAmountPerTx(),
                s.getResidualHandling());
    }

    /** 미설정 사용자 기본값 — 자동환전 OFF, 달러 우선 ON. */
    public static FxAutoSettingResponse defaults() {
        return new FxAutoSettingResponse(false, true, null, null);
    }
}
