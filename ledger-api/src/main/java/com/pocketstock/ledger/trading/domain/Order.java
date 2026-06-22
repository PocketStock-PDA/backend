package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 (orders, DB B 원장). 소수점·온주 공용 테이블.
 * 온주는 round_id·batch_id 없이(NULL) 즉시 체결(RECEIVED→FILLED)하며 price에 체결단가를 기록한다.
 * (소수점은 차수·배치·배분 머신을 거치고 체결가는 batch_orders/allocations에 기록)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    private Long id;
    private String clientOrderId;     // 멱등키(FIX clOrdID)
    private Long userId;
    private Long accountId;
    private String stockCode;
    private String exchange;          // 거래소: KOSPI | KOSDAQ | NASDAQ | NYSE | AMEX (composite FK)
    private String side;              // BUY | SELL
    private String orderType;         // 온주: LIMIT | MARKET (소수점: AMOUNT | QUANTITY | ALL)
    private BigDecimal orderAmount;   // 소수점 금액주문 주문금액(매수 AMOUNT·매도 AMOUNT). 온주는 NULL
    private BigDecimal orderQuantity; // 주문 수량(온주=정수 / 소수점 수량매수·전량매도=소수)
    private BigDecimal estQuantity;   // 소수점 접수 시점 예상 체결수량(참고치). 확정수량은 allocations
    private BigDecimal heldAmount;    // 소수점 접수 시 실제 잠근 금액(D1). 환원·감사 기준. 온주는 NULL
    private BigDecimal price;         // 온주 체결단가(지정가=요청가 / 시장가=최우선호가)
    private OrderStatus status;       // 상태머신(§08/§08b). DB VARCHAR(이름)↔enum 자동 매핑
    private String source;            // MANUAL | AUTO
    private Long roundId;             // 소수점 차수(trading_rounds.id). 온주는 NULL
    private Long batchId;             // 소수점 블록주문(batch_orders.id). 집행 전·온주는 NULL
    private String currency;          // KRW | USD
    private String failReason;        // REJECTED 사유(감사용). 정상 주문은 NULL
    private LocalDateTime requestedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
