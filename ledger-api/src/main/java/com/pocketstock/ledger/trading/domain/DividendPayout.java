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
 * 배당 지급 로그 + 재투자 결과 (dividend_payouts, DB B). 보유자별 배당 지급 1행 = CMA 원화풀 입금.
 *
 * <p>지급은 항상(배당은 사용자 돈), 재투자는 DRIP ON일 때만. {@code (user_id, stock_code, pay_date)} UNIQUE로
 * 같은 지급일 중복 지급을 막는다(멱등). 소액 배당(<최소주문 1,000원)은 CMA 잔돈으로 부족분을 채워
 * {@code reinvestAmount = max(grossAmount, 1000)}으로 매수한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendPayout {

    private Long id;
    private Long userId;
    private String stockCode;
    private LocalDate payDate;
    private BigDecimal holdingQty;       // 지급 시점 보유수량
    private BigDecimal perShare;         // 주당 현금배당금(KRW)
    private BigDecimal grossAmount;      // 지급액 = holdingQty × perShare (CMA 입금액)
    private String status;              // PAID / REINVESTED / REINVEST_FAILED
    private Long reinvestOrderId;
    private BigDecimal reinvestAmount;   // 실제 재매수 금액 = max(grossAmount, 1000)
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 조회 join 전용(테이블 컬럼 아님) ----
    private String stockName;
}
