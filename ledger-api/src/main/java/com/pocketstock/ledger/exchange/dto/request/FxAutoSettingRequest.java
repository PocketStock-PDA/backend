package com.pocketstock.ledger.exchange.dto.request;

import java.math.BigDecimal;

/**
 * 자동환전 설정 변경({@code PUT /api/exchange/auto-settings}) 요청.
 * null 필드는 기본값으로 정규화(autoEnabled=false, useDollarFirst=true)된다.
 */
public record FxAutoSettingRequest(
        Boolean autoEnabled,        // 매수 시 달러 자동환전
        Boolean useDollarFirst,     // 달러풀 우선 사용
        BigDecimal maxAmountPerTx,  // 1회 환전 한도(원), null=무제한
        String residualHandling     // TO_KRW / KEEP_USD
) {
}
