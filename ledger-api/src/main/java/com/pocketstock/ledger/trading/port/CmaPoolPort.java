package com.pocketstock.ledger.trading.port;

import java.math.BigDecimal;

/**
 * 주문 자금이동이 CMA 통화풀에 요구하는 출금/입금 연산 계약 — <b>trading 도메인 소유 인터페이스</b>.
 *
 * <p>매수 충당(CMA풀 → 예수금)·매도 환류(예수금 → CMA풀)에서 <b>CMA풀 leg</b>만 cma에 위임한다.
 * 예수금 leg은 trading이 {@code DepositService}로 직접 처리하므로, 자동충당/환류 오케스트레이션은
 * trading({@code OrderFundingService})이 주도하고 CMA풀만 이 포트로 끊는다 — trading→cma 단방향 의존.
 * cma·trading이 같은 DB B라 호출자({@code OrderFundingService})의 트랜잭션에 합류해
 * <b>하나의 로컬 트랜잭션</b>으로 커밋·롤백된다(Saga 불필요).
 *
 * <p>수동 이체 API({@code CmaTransferService}, cma 주도)와 역할이 다르다 — 이 포트는 <b>주문 트랜잭션</b>
 * 내부의 자동 충당/환류 전용이다. 실제 구현(어댑터)은 <b>CMA 도메인</b>이 제공한다
 * ({@code cma_transactions} BUY_TRANSFER/SELL_RETURN + {@code cma_balances} 갱신·멱등은 CMA가 소유).
 *
 * <p>계좌 비밀번호(거래 인증)는 이 포트의 책임이 아니다 — 호출자가 주문 진입 시 {@code TxnAuthGuard}로 1회 처리한다.
 */
public interface CmaPoolPort {

    /**
     * 매수 충당 — CMA {@code currency} 풀에서 {@code amount} 출금(BUY_TRANSFER, 음수 leg).
     * 잔액 부족이면 {@code INSUFFICIENT_BALANCE}로 막혀 호출자 트랜잭션이 롤백된다.
     *
     * @param orderId 연계 주문(원장 ref_id=orders.id 추적용, 같은 DB B)
     * @param idempotencyKey 결정적 멱등키(예: {@code order:{id}:fund}) — UNIQUE로 이중 출금 차단
     * @return 출금 후 CMA 풀 잔액
     */
    BigDecimal withdrawForBuy(Long userId, String currency, BigDecimal amount,
                              Long orderId, String idempotencyKey);

    /**
     * 매도 환류 — CMA {@code currency} 풀에 {@code amount} 입금(SELL_RETURN, 양수 leg).
     *
     * @param orderId 연계 주문(원장 ref_id=orders.id 추적용, 같은 DB B)
     * @param idempotencyKey 결정적 멱등키(예: {@code order:{id}:return}) — UNIQUE로 이중 입금 차단
     * @return 입금 후 CMA 풀 잔액
     */
    BigDecimal depositFromSell(Long userId, String currency, BigDecimal amount,
                               Long orderId, String idempotencyKey);

    /**
     * 충당 반납 — 안 쓴(취소·가격개선) 충당분을 CMA {@code currency} 풀로 되돌린다(REVERT, 양수 leg).
     * 매수 충당(BUY_TRANSFER)의 보상거래다. 예수금 출금 leg은 호출자가 함께 처리한다.
     *
     * @param idempotencyKey 결정적 멱등키(예: {@code order:{id}:cancel:cma}) — UNIQUE로 이중 반납 차단
     * @return 반납 후 CMA 풀 잔액
     */
    BigDecimal revertBuyTransfer(Long userId, String currency, BigDecimal amount,
                                 Long orderId, String idempotencyKey);

    /**
     * CMA 통화풀 현재 잔액(읽기 전용) — 충당 필요분 계산·멱등 재요청 응답용. 풀이 없으면 0.
     */
    BigDecimal poolBalance(Long userId, String currency);
}
