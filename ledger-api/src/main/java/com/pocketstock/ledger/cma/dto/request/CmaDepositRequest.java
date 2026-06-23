package com.pocketstock.ledger.cma.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 은행계좌 → CMA 원화풀 부족분 충전({@code POST /api/cma/deposit}) 요청.
 *
 * <p>매수 화면에서 "사려는 금액({@code targetAmount})"을 보내면 서버가 현재 CMA 원화풀 잔액을 빼
 * <b>부족분만</b> 은행계좌에서 끌어와 충전한다(차액은 서버가 계산 — 클라가 들고 있던 잔액이 낡아도 안전).
 * 이미 충분하면 이체하지 않는다. KRW 전용 — 해외(USD)는 매수 시점 자동환전이 담당한다.
 *
 * <p>거래 인증은 본문 비밀번호가 아니라 사전 거래 세션(txn-auth)으로 처리한다.
 * {@code idempotencyKey}는 클라 발급 멱등키 — 따닥 탭·재전송 시 같은 값을 보내면 기존 충전 결과를 반환한다.
 */
public record CmaDepositRequest(
        @NotNull @Positive BigDecimal targetAmount,  // 사려는 금액(KRW). 충전 목표 = 이 금액
        @NotNull Long sourceAccountId,               // 충전 재원이 될 연동 은행계좌 ID
        String idempotencyKey
) {
}
