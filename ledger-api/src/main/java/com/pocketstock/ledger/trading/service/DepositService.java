package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.DepositTransaction;
import com.pocketstock.ledger.trading.mapper.DepositMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 예수금 처리. 현재잔액은 {@code account_balances}(계좌당 1행, 가변)에서 원자 갱신하고,
 * 거래 역사는 {@code deposit_transactions}(불변 journal, append-only)에 1줄씩 쌓는다.
 * 잔액행은 계좌개설 때 balance=0으로 생성된다(여기선 항상 UPDATE만).
 */
@Service
@RequiredArgsConstructor
public class DepositService {

    private final DepositMapper depositMapper;

    /**
     * 예수금 1건 반영 — 잔액 원자 갱신(출금 음수 가드) 후 역사 1줄 append, 갱신된 잔액 반환.
     * @param signedAmount +입금(SELL/IN_TRANSFER) / −출금(BUY). 출금이 잔액 초과면 INSUFFICIENT_BALANCE.
     */
    @Transactional
    public BigDecimal record(Long userId, Long accountId, String txType, BigDecimal signedAmount,
                             String currency, String refType, Long refId) {
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
                .build());
        return after;
    }
}
