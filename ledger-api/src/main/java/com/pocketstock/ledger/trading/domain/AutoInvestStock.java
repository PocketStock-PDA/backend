package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 종목별 자동모으기 = 주기 base (auto_invest_stocks, DB B, 1인 N행).
 * 종목 1행 = 주기(필수). 물타기/익절 트리거는 옵션으로 auto_invest_triggers에 별도(레이어 구조).
 * 독립형: 종목 1개 = 설정 1행, {@code (user_id, stock_code)} UNIQUE.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoInvestStock {

    private Long id;
    private Long userId;
    private Long accountId;
    private String stockCode;
    private String market;        // DOMESTIC / OVERSEAS
    private String period;        // DAILY / WEEKLY / MONTHLY
    private Integer periodDay;    // DAILY=null · WEEKLY 요일 1~5 · MONTHLY 1~31
    private String amountType;    // AMOUNT(금액) / QUANTITY(수량)
    private BigDecimal buyAmount; // 정기 1회 매수금액 (AMOUNT)
    private BigDecimal buyQuantity; // 정기 1회 매수수량 (QUANTITY)
    private String currency;      // KRW / USD
    private Boolean isActive;     // 이 종목 자동모으기 ON/OFF
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String stockName;     // 조회 join 전용(테이블 컬럼 아님)
}
