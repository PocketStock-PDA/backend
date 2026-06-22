package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 소수점 배치 차수 마스터 (trading_rounds, DB B 원장). 매 분 market별 1행(#101 1분 차수).
 * 접수가 현재 분 차수에 주문을 QUEUED로 편입하고(find-or-create), 스케줄러가 실행시각 도달분을
 * 단일 실행 선점(OPEN→EXECUTING→SETTLED)으로 집행한다. 장외·휴장 구분 없이 24/7 상시(D5, RESERVED 폐기).
 *
 * <p>{@code (market, round_no, trade_date)} UNIQUE — 같은 분 동시 접수는 find-or-create가 단일행으로 수렴.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingRound {

    private Long id;
    private String market;            // DOMESTIC | OVERSEAS — 체결 엔진 분기
    private String roundNo;           // 1분 차수(UTC yyyyMMddHHmm)
    private LocalDate tradeDate;      // 거래일(UTC)
    private LocalDateTime submitOpen; // 신청 접수 시작(분 시작)
    private LocalDateTime submitClose;// 신청 마감(분 끝)
    private LocalDateTime executeAt;  // 실행=집행 시각(분 끝)
    private LocalDateTime settleAt;   // 체결 확정 시각(시뮬 즉시 = executeAt)
    private LocalDateTime cancelDeadline; // 취소 가능 시한(= executeAt)
    private String pricingMethod;     // MIXED(실제 가격은 batch_orders별: DOMESTIC_TICK/MARKET)
    private String status;            // OPEN | EXECUTING | SETTLED | FAILED
    private LocalDateTime createdAt;
}
