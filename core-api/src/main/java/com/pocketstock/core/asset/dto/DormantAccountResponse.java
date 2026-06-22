package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;

/**
 * 휴면 은행 계좌 단건(is_dormant=true).
 * 해지 요청(POST /assets/dormant/close)의 식별자로 accountId를 노출한다.
 * 계좌번호(account_no_enc)는 암호화 컬럼이라 노출하지 않고, 표시는 은행명 + 상품명(accountName)으로 한다
 * (기존 BankAccountResponse 관례 동일). 와이어프레임 표기 = 은행명 / 상품명 / 금액.
 */
public record DormantAccountResponse(
        Long accountId,
        String bankName,
        String accountName,
        BigDecimal balance,
        String currency
) {}
