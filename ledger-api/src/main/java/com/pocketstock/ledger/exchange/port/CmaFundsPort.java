package com.pocketstock.ledger.exchange.port;

import java.math.BigDecimal;

/**
 * 환전 체결이 CMA 풀(원화/달러)에 요구하는 자금 연산 계약 — <b>exchange 도메인 소유 인터페이스</b>.
 *
 * <p>환전 = CMA 내부 통화 스왑(ERD-07 §고민-11): from풀 차감(FX_OUT) + to풀 입금(FX_IN)을
 * 호출자({@code ExchangeSettleService})의 트랜잭션에 합류시켜 <b>하나의 DB B 로컬 트랜잭션</b>으로
 * 처리한다(Saga 불필요, 실패 시 롤백). cma·exchange가 같은 DB B라 가능.
 *
 * <p>실제 구현(어댑터)은 <b>CMA 도메인(담당: 강문군)</b>이 제공한다 —
 * {@code cma_transactions}(FX_OUT/FX_IN, ref_type='FX_TX', ref_id=fxTransactionId) 적재 +
 * {@code cma_balances} 갱신 + append-only/멱등 등 원장 규칙은 CMA가 소유한다.
 * 실 어댑터가 빈으로 등록되기 전까진 {@link DevCmaFundsAdapter}(로컬 테스트용 스텁)가 대신한다.
 *
 * <p>계좌 비밀번호(거래 인증)는 이 포트의 책임이 아니다 — 호출자(체결 서비스)가 거래 전
 * 공용 거래 인증 가드({@code TxnAuthGuard}, 30분 txn-auth 세션)로 처리한다.
 */
public interface CmaFundsPort {

    /**
     * 환전 양다리 — {@code fromCurrency} 풀에서 {@code fromAmount} 차감(FX_OUT),
     * {@code toCurrency} 풀에 {@code toAmount} 입금(FX_IN). 호출자 트랜잭션에 합류.
     *
     * @param fxTransactionId 두 원장 줄을 묶는 fx_transactions.id (ref_id)
     * @return 체결 후 from/to 풀 잔액
     * @throws com.pocketstock.common.exception.BusinessException 잔액 부족·계좌 없음 등
     */
    FxLegResult applyFxLegs(Long userId,
                            String fromCurrency, BigDecimal fromAmount,
                            String toCurrency, BigDecimal toAmount,
                            Long fxTransactionId);

    /**
     * CMA 통화풀 현재 잔액 조회(읽기 전용) — 멱등 재요청 시 기존 결과의 잔액 필드를 채우기 위함.
     * 체결 경로가 아니라 잔액을 안 건드린다(원장 이동 없음). 풀이 아직 없으면 0.
     *
     * @throws com.pocketstock.common.exception.BusinessException CMA 계좌 없음 등
     */
    BigDecimal poolBalance(Long userId, String currency);
}
