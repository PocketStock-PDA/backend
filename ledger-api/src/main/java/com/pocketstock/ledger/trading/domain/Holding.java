package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 보유 종목·잔고 (holdings, DB B 원장). (account_id, stock_code) UNIQUE.
 * quantity=보유 주수(내부원장 6자리), avg_buy_price=평균매입단가. 평가·수익률은 daily_valuations(배치).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding {

    private Long id;
    private Long userId;
    private Long accountId;
    private String stockCode;
    private BigDecimal quantity;        // 총 보유(온주+소수 합)
    private BigDecimal fractionalQty;   // 소수점(신탁) 보유분(자유 누적, ≥1 가능). 온주(직접소유) = quantity − fractionalQty
    private BigDecimal heldWhole;       // 온주 매도 예약(M2). 온주 매도가능 = (quantity−fractionalQty) − heldWhole
    private BigDecimal heldFractional;  // 소수 매도 예약(M2). 소수 매도가능 = fractionalQty − heldFractional
    private BigDecimal avgBuyPrice;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
