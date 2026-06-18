package com.pocketstock.ledger.exchange.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 환전 거래 기록(fx_transactions) — 방향·금액·적용환율·상태를 박제한다.
 * 실제 잔액 변동은 CMA(원화풀/달러풀)에 있고, 여기는 "환전 행위"만 기록한다.
 *
 * <p>{@code exchangeRate}는 스프레드·우대가 내재된 적용환율, {@code fee}=0(비용은 환율 내재).
 * MVP는 같은 DB B 로컬 트랜잭션으로 즉시 체결 → {@code status='DONE'}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FxTransaction {

    private Long id;
    private Long userId;
    private String fromCurrency;     // KRW / USD
    private String toCurrency;       // USD / KRW
    private BigDecimal fromAmount;
    private BigDecimal toAmount;
    private BigDecimal exchangeRate; // 적용환율(체결 박제)
    private BigDecimal fee;          // 0 (비용은 환율 내재)
    private String triggerType;      // MANUAL / AUTO / RESIDUAL
    private Long refOrderId;         // AUTO 환전 시 연계 매수주문
    private String status;           // DONE / FAILED
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
