package com.pocketstock.ledger.exchange.dto.response;

import java.math.BigDecimal;

/**
 * 내부 호출(core 잔돈 스캔)용 환율 뷰 — 외화 잔돈 KRW 환산에 매매기준율만 필요.
 * 공개 {@code GET /api/exchange/rate}(ApiResponse 래핑)와 달리 raw 반환.
 */
public record UsdKrwRateView(
        BigDecimal baseRate
) {}
