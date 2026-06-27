package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 배당 자동 재투자(DRIP) 토글 (dividend_reinvest_settings, DB B) — 종목별 ON/OFF.
 *
 * <p>ON이면 그 종목 배당 지급 시 받은 배당으로 같은 종목을 재매수(소액은 CMA 잔돈으로 부족분 충당해 최소주문 채움).
 * OFF(기본)면 배당금이 CMA 원화풀에 현금으로 남는다(현금 수령). {@code (user_id, stock_code)} UNIQUE.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendReinvestSetting {

    private Long id;
    private Long userId;
    private String stockCode;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 조회 join 전용(테이블 컬럼 아님) — tradable_stocks 조인 표시용 ----
    private String stockName;
}
