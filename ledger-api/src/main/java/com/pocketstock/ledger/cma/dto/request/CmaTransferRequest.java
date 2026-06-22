package com.pocketstock.ledger.cma.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * CMA 풀 → 위탁 예수금 자금이동({@code POST /api/cma/transfer}) 요청.
 *
 * <p>{@code market}이 출금 통화풀과 입금 예수금을 함께 결정한다(DOMESTIC=KRW풀→KRW예수금,
 * OVERSEAS=USD풀→USD예수금). 거래 인증은 본문 비밀번호가 아니라 사전 거래 세션(txn-auth)으로 처리한다.
 * {@code idempotencyKey}는 클라 발급 멱등키 — 따닥 탭·재전송 시 같은 값을 보내면 기존 이체 결과를 반환한다.
 */
public record CmaTransferRequest(
        @NotBlank String market,             // DOMESTIC | OVERSEAS
        @NotNull @Positive BigDecimal amount, // 이체할 금액(market 통화)
        String idempotencyKey
) {
}
