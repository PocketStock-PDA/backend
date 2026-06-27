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
 * 만기 후 배당주 매수 예약 (maturity_buy_reservations, DB B).
 *
 * <p>예적금 만기일이 도래하면 추천 배당주를 자동 매수하는 사용자 예약. 자동모으기와 동형 —
 * 예약 1행 + 일배치 스케줄러({@code MaturityReservationScheduler})가 만기일에 {@code place(source=MATURITY)}로 집행.
 * 트리거(만기일)는 생성 시점에 서버가 연동은행계좌(DB A)에서 읽어 스냅샷으로 고정한다(클라 신뢰 X).
 *
 * <p>슬라이더의 "예금 재예치 X%"는 정보성(실 재예치 시뮬 불가)이라 백엔드는 배당주 매수분({@code buyAmount})만 담는다.
 * 자금은 만기 계좌 원금에서 CMA로 충전 후 매수. 국내(KRW) 전용 — 해외(USD/환전)는 추후.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaturityBuyReservation {

    private Long id;
    private Long userId;
    private Long linkedBankAccountId;  // 만기 트리거 겸 자금 출처 연동은행계좌(DB A id 스냅샷)
    private LocalDate maturityDate;    // 집행 트리거일(생성 시 서버 스냅샷)
    private String stockCode;
    private String market;             // DOMESTIC
    private String currency;           // KRW
    private BigDecimal buyAmount;      // 매수금액(KRW)
    private String status;             // RESERVED / EXECUTED / FAILED / CANCELLED
    private Long orderId;              // 집행 성공 시 생성 주문 id
    private String failReason;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 조회 join 전용(테이블 컬럼 아님) — tradable_stocks 조인 표시용 ----
    private String stockName;
}
