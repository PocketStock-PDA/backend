package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 평균가 비례배분 (allocations, DB B 원장) — 블록 체결수량을 자식주문 비율로 6자리 배분한 이력(append-only).
 * holdings·예수금 갱신의 근거. 수수료·세금 미반영(D6)이라 net_amount = gross_amount.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Allocation {

    private Long id;
    private Long orderId;
    private Long batchOrderId;
    private BigDecimal allocatedQty;
    private BigDecimal allocatedPrice;   // = batch fill_price
    private BigDecimal grossAmount;      // allocatedQty × allocatedPrice
    private BigDecimal fee;              // 0 (D6)
    private BigDecimal tax;              // 0 (D6)
    private BigDecimal netAmount;        // gross ± fee ± tax = gross
    private LocalDateTime createdAt;
}
