package com.pocketstock.ledger.cma.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CmaTransaction {

    private Long id;
    private Long userId;
    private Long cmaAccountId;
    private String currency;
    // tx_type 정식 어휘(단일 출처) — 입금(+): DEPOSIT, COLLECT, INTEREST, BANK_IN, DORMANT, SAVINGS, SELL_RETURN, FX_IN
    //                                 출금(-): BUY_TRANSFER, FX_OUT / 정정: REVERT
    // DEPOSIT=사용자 수동 입금·초기 충전(source MANUAL), BANK_IN=연동 은행계좌발 자동 입금(source ACCOUNT)
    // FX_IN/FX_OUT은 환전 CmaFundsPort 계약(ref_type='FX_TX', ref_id=fx_transactions.id, 스왑당 2줄)을 따른다.
    private String txType;
    // source_type 정식 어휘 — 거래 출처(모든 행). 수집: ACCOUNT(끝전)/CARD(라운드업)/POINT,
    //                          그 외: MANUAL(수동 입금)/SYSTEM(이자 등). collection_settings.source_type와 동일 어휘.
    private String sourceType;

    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String refType;
    private Long refId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
