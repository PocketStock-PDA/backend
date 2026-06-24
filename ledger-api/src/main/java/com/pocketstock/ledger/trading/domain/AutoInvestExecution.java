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
 * 자동모으기 실행 회차 로그 (auto_invest_executions, DB B). 와이어 "모으기 내역" 화면.
 * 회차별 체결/실패를 전부 1행씩 — 실패(접수실패·주문가능금액 부족)도 회차 번호를 받는다(orders FK로는 못 담음).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoInvestExecution {

    private Long id;
    private Long autoInvestStockId;
    private Integer roundNo;        // 종목별 회차(1씩 증가)
    private String triggerSource;  // PERIODIC / DIP_BUY / TAKE_PROFIT
    private String side;           // BUY / SELL
    private LocalDate execDate;
    private String status;         // FILLED(체결) / FAILED(접수 실패)
    private String failReason;     // FAILED 사유 (INSUFFICIENT_FUNDS 등)
    private Long orderId;          // 성공 시 대표 주문 id (실패 null)
    private BigDecimal execAmount; // 체결 금액
    private BigDecimal execQuantity; // 체결 수량
    private String currency;
    private LocalDateTime createdAt;
}
