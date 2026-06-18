package com.pocketstock.ledger.exchange.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 자동환전 설정(fx_auto_settings) — 1인 1행.
 * 미국주식 매수 시 달러 부족분 자동환전 여부·달러풀 우선사용·1회 한도·외화잔돈 처리.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FxAutoSetting {

    private Long id;
    private Long userId;
    private Boolean isAutoEnabled;     // 매수 시 달러 자동환전
    private Boolean useDollarFirst;    // 달러풀 우선 사용 후 부족분만 환전
    private BigDecimal maxAmountPerTx; // 1회 최대 환전 한도(원), null=무제한
    private String residualHandling;   // 외화잔돈: TO_KRW / KEEP_USD
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
