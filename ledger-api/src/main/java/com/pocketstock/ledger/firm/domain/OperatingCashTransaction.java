package com.pocketstock.ledger.firm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 회사 현금 원장 (operating_cash_transactions, DB B 원장, append-only).
 * 복식부기의 상대계정 — 유저 예수금 leg(deposit_transactions)의 반대 부호 짝.
 * 잔액은 행마다 balance_after 스냅샷, 현재잔액은 operating_cash_balances(통화당 1행)가 보유.
 *
 * <p>전사(firm) 장부 — 특정 도메인 전용이 아니다. trading 매매 현금 leg(H1)과
 * exchange 환전 통화풀 leg(H5)이 같은 테이블에 흡수되며, ref_type으로 출처를 구분한다.
 * tx_type: BUY(매수 → 회사 현금 수취,+) / SELL(매도 → 지급,−) / FX(환전 통화풀 leg, 부호로 방향).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatingCashTransaction {

    private Long id;
    private String currency;
    private String txType;
    private BigDecimal amount;        // +수취 / −지급
    private BigDecimal balanceAfter;
    private String refType;          // order | allocation | batch | fx
    private Long refId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
