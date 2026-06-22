package com.pocketstock.core.asset.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** linked_bank_accounts INSERT용(생성 id 회수). 연동 시 템플릿 계좌 적재. */
@Getter
@Setter
public class LinkedBankAccountInsert {
    private Long id;
    private Long userId;
    private Long institutionId;     // linked_institutions.id
    private String accountType;     // DEMAND/SAVINGS/DEPOSIT
    private String accountName;
    private BigDecimal balance;
    private String currency;        // KRW/USD
}
