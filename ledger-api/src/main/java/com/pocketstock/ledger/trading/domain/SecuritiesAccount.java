package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 증권 위탁계좌 (securities_accounts, DB B 원장).
 * market = DOMESTIC | OVERSEAS, user당 market별 1행(UNIQUE user_id+market).
 * account_no_enc = 계좌번호 암호문(VARBINARY) → {@link com.pocketstock.ledger.trading.support.AccountNoCipher}로 복호화.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecuritiesAccount {

    private Long id;
    private Long userId;
    private String market;            // DOMESTIC | OVERSEAS
    private byte[] accountNoEnc;      // 계좌번호 암호문
    private String status;            // ACTIVE | ...
    private Boolean isFractionalEnabled;
    private LocalDateTime openedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
