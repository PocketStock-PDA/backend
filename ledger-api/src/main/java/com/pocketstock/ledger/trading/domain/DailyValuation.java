package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일별 평가 스냅샷 (daily_valuations, DB B, BATCH-002). holdings는 lean이라 평가·수익률 추이를 담을 곳이 없어
 * 매일 종가로 박제(차트·히스토리용). **종가 기준 native 평가손익** — 환차손익(현재환율)은 제외(live /holdings 몫).
 * {@code (user_id, stock_code, eval_date)} UNIQUE.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyValuation {

    private Long id;
    private Long userId;
    private String stockCode;
    private LocalDate evalDate;
    private BigDecimal quantity;      // 그날 보유 수량(온주+소수 합)
    private BigDecimal closePrice;    // 종가(종목 통화)
    private BigDecimal evalAmount;    // = quantity × 종가
    private BigDecimal profitAmount;  // 평가손익 = eval − 매입원가(quantity × avg_buy_price)
    private BigDecimal profitRate;    // 수익률 % = (종가 − avg)/avg × 100
    private String currency;          // KRW / USD
    private LocalDateTime createdAt;
}
