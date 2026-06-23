package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 블록(합산) 주문 (batch_orders, DB B 원장) — 동일종목·차수·side·가격모델의 소수점 주문을 합산해
 * 회사가 시장에 낸 온주(정수) 주문 1건 + 그 시뮬 체결 결과. 부분체결 없어 체결가/시각을 직접 보유.
 *
 * <p>{@code whole_qty} = 회사 선부담 ceil(매수)/floor(매도). {@code net_fractional_qty} = 상계 후 순 소수주(=Σ배분).
 * 자체 시뮬이라 LS 주문번호({@code ls_order_id}/{@code ls_exec_id})는 비운다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchOrder {

    private Long id;
    private String stockCode;
    private String exchange;
    private String side;                  // BUY | SELL
    private Long roundId;
    private String pricingMethod;         // DOMESTIC_TICK | MARKET
    private BigDecimal netFractionalQty;  // 상계 후 순 소수주(Σ 배분수량)
    private Integer wholeQty;             // 시장 체결주수 = 매수 ceil / 매도 floor
    private String lsOrderId;             // 자체 시뮬 — null
    private String lsExecId;              // 자체 시뮬 — null
    private String status;                // BUILDING | SENT | FILLED | FAILED
    private BigDecimal fillPrice;         // 체결단가 = 배분 기준가
    private LocalDateTime sentAt;
    private LocalDateTime filledAt;
    private LocalDateTime createdAt;
}
