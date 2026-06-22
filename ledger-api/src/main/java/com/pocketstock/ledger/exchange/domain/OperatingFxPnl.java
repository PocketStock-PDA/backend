package com.pocketstock.ledger.exchange.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 회사 환차익 원장 (operating_fx_pnl, DB B 원장, append-only, H5 #96).
 * 환전 스프레드(매매기준율 mid vs 고객 적용환율)의 실현 손익을 환전 1건당 1줄로 박제한다.
 *
 * <p>회사 통화풀(operating_cash_*)은 from풀 +/to풀 − 2-leg으로 통화별 보존(Σ=0)을 만들고,
 * 그 2-leg을 base_currency(KRW) 기준으로 환산하면 비대칭이 남는다(= 회사가 가져간 스프레드).
 * 그 금액이 {@code realizedPnl}이다. 통화별 보존과 별개의 P&L 측정치라 conservation에는 끼지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatingFxPnl {

    private Long id;
    private Long fxTransactionId;
    private String fromCurrency;
    private String toCurrency;
    private String baseCurrency;     // 손익 측정 통화(KRW)
    private BigDecimal realizedPnl;  // base_currency 기준, + = 회사 이익
    private BigDecimal midRate;      // 매매기준율 스냅샷
    private BigDecimal appliedRate;  // 고객 적용환율(buyRate/sellRate)
    private String idempotencyKey;   // fx:{id}:pnl
    private LocalDateTime createdAt;
}
