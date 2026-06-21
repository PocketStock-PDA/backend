package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.DepositTransaction;
import com.pocketstock.ledger.trading.mapper.DepositMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 예수금 처리. 현재잔액은 {@code account_balances}(계좌당 1행, 가변)에서 원자 갱신하고,
 * 거래 역사는 {@code deposit_transactions}(불변 journal, append-only)에 1줄씩 쌓는다.
 * 잔액행은 계좌개설 때 balance=0으로 생성된다(여기선 항상 UPDATE만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositService {

    private final DepositMapper depositMapper;

    /** 계좌 예수금 현재잔액. 멱등 재요청 시 기존 주문 결과를 돌려줄 때 사용. */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long accountId) {
        return depositMapper.findBalanceByAccount(accountId);
    }

    /**
     * 매수 PENDING 진입 시 예수금 hold(M2) — 잔액은 그대로, 주문가능(balance−held)만 줄인다.
     * 주문가능 부족이면 INSUFFICIENT_BALANCE. 체결 시 released+실차감, 취소·미체결 시 release.
     */
    @Transactional
    public void hold(Long accountId, BigDecimal amount) {
        if (depositMapper.addHold(accountId, amount) == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "주문가능 예수금이 부족합니다.");
        }
    }

    /** hold 환원 — 취소·미체결 만료 시 묶인 금액을 주문가능으로 되돌린다(잔액 불변). */
    @Transactional
    public void releaseHold(Long accountId, BigDecimal amount) {
        // 0행 = 언더플로우 가드에 막힘(환원>잔여 hold) → hold 회계 버그. 정합성 오염은 가드가 이미 막음.
        if (depositMapper.releaseHold(accountId, amount) == 0) {
            log.warn("hold 환원 실패(0행) — held 회계 불일치 의심 accountId={} amount={}", accountId, amount);
        }
    }

    /**
     * 예수금 1건 반영 — 잔액 원자 갱신(출금 음수 가드) 후 역사 1줄 append, 갱신된 잔액 반환.
     * @param signedAmount +입금(SELL/IN_TRANSFER) / −출금(BUY). 출금이 잔액 초과면 INSUFFICIENT_BALANCE.
     * @param idempotencyKey 같은 키 재적재는 UNIQUE로 차단(결정적 키, 예: order:{orderId}). null 허용.
     */
    @Transactional
    public BigDecimal record(Long userId, Long accountId, String txType, BigDecimal signedAmount,
                             String currency, String refType, Long refId, String idempotencyKey) {
        // ① 잔액 원자 갱신 + 출금 음수 가드. affected=0 → 잔액부족(또는 잔액행 없음=계좌개설 누락).
        int affected = depositMapper.applyBalanceDelta(accountId, signedAmount);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "예수금이 부족합니다.");
        }
        // ② 갱신된 잔액을 박제해 불변 역사 1줄 append (같은 트랜잭션 — 자기 UPDATE 결과를 읽음).
        BigDecimal after = depositMapper.findBalanceByAccount(accountId);
        depositMapper.insert(DepositTransaction.builder()
                .userId(userId)
                .accountId(accountId)
                .txType(txType)
                .amount(signedAmount)
                .currency(currency)
                .balanceAfter(after)
                .refType(refType)
                .refId(refId)
                .idempotencyKey(idempotencyKey)
                .build());
        return after;
    }
}
