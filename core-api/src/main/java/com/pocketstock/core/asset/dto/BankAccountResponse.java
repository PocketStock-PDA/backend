package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;

/**
 * 유저 보유(연동) 은행 계좌 단건.
 * 계좌 1원 인증·출금/충전 재원 계좌 선택 등 여러 화면에서 공용으로 쓰는 조회 응답.
 * 계좌번호(account_no_enc)는 암호화 컬럼이라 노출하지 않고, 식별은 bankName + accountName으로 한다.
 */
public record BankAccountResponse(
        Long accountId,
        String bankCode,
        String bankName,
        String accountName,
        String accountType,
        BigDecimal balance,
        String currency,
        boolean isDormant,
        boolean isVerified
) {}
