package com.pocketstock.ledger.cma.port;

import java.math.BigDecimal;

/**
 * trading(매수/정산) 도메인이 호출하는 <b>은행계좌 → CMA 원화풀 충전</b> 연산 계약 — <b>CMA 도메인 소유 인터페이스</b>.
 *
 * <p>"부족금액 자동충전"의 한 다리(은행 → CMA)를 trading의 매수 오케스트레이션이 끌어다 쓰기 위한 진입점이다.
 * 매수 흐름이 예수금 부족을 감지하면 ① 이 포트로 은행 → CMA 충전 → ② CMA → 위탁 예수금 이동(BUY_TRANSFER,
 * {@link DepositFundsPort}) → ③ 매수로 이어간다. 부족 감지·연쇄·예수금 차감은 trading이 소유하고,
 * 이 포트는 은행 차감(core-api/DB A Feign) + CMA 원장 입금(DB B)만 책임진다.
 *
 * <p><b>사용자가 직접 누르는 충전</b>({@code POST /api/cma/deposit})과 달리 이 포트는 <b>내부 연쇄용</b>이다 —
 * 거래 인증(txn-auth)은 호출자(매수)가 이미 처리했다고 보고 <b>재요구하지 않으며</b>, 목표 잔액이 아니라
 * 호출자가 계산한 <b>정확한 충전 금액(amount)</b>을 그대로 옮긴다. 멱등키로 중복 충전을 막는다.
 *
 * <p>구현은 CMA 도메인({@code CmaBankChargeService})이 제공하고, trading은 이 인터페이스만 주입해 호출한다
 * (trading→cma 단방향, {@link DepositFundsPort}과 동형). 호출자 트랜잭션에 합류한다(같은 DB B 로컬 트랜잭션).
 */
public interface CmaChargePort {

    /**
     * 은행계좌 → CMA 원화풀 충전 — {@code sourceAccountId} 계좌에서 {@code amount}(KRW)만큼 끌어와 CMA 원화풀에 입금한다.
     * CMA 원장 입금(DEPOSIT) 후 은행 잔액을 차감한다(차감 실패 시 호출자 트랜잭션 롤백 → 부분 반영 없음).
     *
     * @param sourceAccountId 충전 재원이 될 연동 은행계좌 ID(본인 소유·미해지여야 함)
     * @param amount 옮길 금액(KRW, 양수). 호출자가 계산한 부족분
     * @param idempotencyKey 같은 키 재요청은 재충전 없이 기존 결과 반환(중복 충전 차단)
     * @return 충전 결과(실제 충전액 + 충전 후 CMA 원화 잔액)
     * @throws com.pocketstock.common.exception.BusinessException CMA 계좌 없음(NOT_FOUND),
     *         미소유/해지 계좌(INVALID_INPUT), 은행 잔액 부족(INSUFFICIENT_BALANCE)
     */
    ChargeResult charge(Long userId, Long sourceAccountId, BigDecimal amount, String idempotencyKey);

    /** 충전 결과 — {@code chargedAmount}=실제 옮긴 금액, {@code cmaBalanceAfter}=충전 후 CMA 원화풀 잔액. */
    record ChargeResult(BigDecimal chargedAmount, BigDecimal cmaBalanceAfter) {}
}
