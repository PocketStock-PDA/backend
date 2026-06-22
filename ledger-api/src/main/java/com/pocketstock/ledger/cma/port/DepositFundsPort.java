package com.pocketstock.ledger.cma.port;

import java.math.BigDecimal;

/**
 * CMA→위탁 예수금 자금이동이 trading 도메인(예수금)에 요구하는 입금 연산 계약 — <b>CMA 도메인 소유 인터페이스</b>.
 *
 * <p>자금이동(BUY_TRANSFER) = CMA 풀 차감 + 위탁 예수금 입금(IN_TRANSFER)을 호출자
 * ({@code CmaTransferService})의 트랜잭션에 합류시켜 <b>하나의 DB B 로컬 트랜잭션</b>으로 처리한다.
 * cma·trading이 같은 DB B라 Saga 불필요(실패 시 롤백). 환전의 {@code CmaFundsPort}와 방향만 반대인 동형 구조.
 *
 * <p>실제 구현(어댑터)은 <b>trading 도메인(예수금 소유)</b>이 제공한다 — 위탁계좌(market) 해석,
 * {@code deposit_transactions}(IN_TRANSFER) append + {@code account_balances} 갱신·멱등은 trading이 소유한다.
 *
 * <p>계좌 비밀번호(거래 인증)는 이 포트의 책임이 아니다 — 호출자가 거래 전 {@code TxnAuthGuard}로 처리한다.
 */
public interface DepositFundsPort {

    /**
     * 위탁 예수금 입금(IN_TRANSFER) — {@code market} 위탁계좌의 예수금을 {@code amount}만큼 늘린다.
     * 호출자 트랜잭션에 합류한다.
     *
     * @param market DOMESTIC(KRW) | OVERSEAS(USD) — 입금 대상 위탁계좌 시장
     * @param currency 예수금 통화(market 파생, 원장 기록용)
     * @param idempotencyKey 같은 키 재적재는 UNIQUE로 차단(자금이동 1건당 결정적 키)
     * @return 입금 후 예수금 잔액
     * @throws com.pocketstock.common.exception.BusinessException 위탁계좌 없음 등
     */
    BigDecimal creditDeposit(Long userId, String market, String currency,
                             BigDecimal amount, String idempotencyKey);

    /**
     * 위탁 예수금 현재잔액(읽기 전용) — 멱등 재요청 시 기존 결과의 잔액 필드를 채우기 위함.
     * 잔액을 건드리지 않는다(원장 이동 없음).
     *
     * @throws com.pocketstock.common.exception.BusinessException 위탁계좌 없음 등
     */
    BigDecimal depositBalance(Long userId, String market);
}
