package com.pocketstock.core.client.dto;

import java.math.BigDecimal;

/**
 * ledger 환율(USD/KRW) 읽기 뷰 — 외화 잔돈(FX) KRW 환산에 매매기준율만 사용.
 * 환산 기준은 CMA 홈 totalKrwEquivalent과 동일(매매기준율).
 */
public record UsdKrwRateView(
        BigDecimal baseRate
) {}
