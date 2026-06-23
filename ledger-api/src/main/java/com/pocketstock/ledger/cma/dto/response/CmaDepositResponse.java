package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;

/**
 * 은행계좌 → CMA 원화풀 부족분 충전 응답.
 *
 * <p>{@code sufficient=true}면 CMA 잔액이 이미 목표 이상이라 이체하지 않은 경우로,
 * {@code depositAmount=0}이고 은행계좌 차감도 없다. {@code sufficient=false}면 부족분만큼 충전한 경우다.
 */
public record CmaDepositResponse(
        String currency,             // "KRW"
        BigDecimal targetAmount,     // 사려는 금액(요청 echo)
        BigDecimal depositAmount,    // 실제 충전된 부족분(이미 충분하면 0)
        BigDecimal cmaBalanceAfter,  // 충전 후 CMA 원화풀 잔액
        boolean sufficient           // true=이미 충분(이체 없음) / false=부족분 충전함
) {
    /** 이미 충분 — 이체하지 않음. */
    public static CmaDepositResponse sufficient(BigDecimal targetAmount, BigDecimal cmaBalance) {
        return new CmaDepositResponse("KRW", targetAmount, BigDecimal.ZERO, cmaBalance, true);
    }

    /** 부족분 충전 완료. */
    public static CmaDepositResponse charged(BigDecimal targetAmount, BigDecimal depositAmount,
                                             BigDecimal cmaBalanceAfter) {
        return new CmaDepositResponse("KRW", targetAmount, depositAmount, cmaBalanceAfter, false);
    }
}
