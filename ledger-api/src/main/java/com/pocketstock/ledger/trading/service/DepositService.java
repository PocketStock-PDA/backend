package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.trading.domain.DepositTransaction;
import com.pocketstock.ledger.trading.mapper.DepositMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 예수금 원장(append-only) 공통 처리. 잔액은 행마다 balance_after 스냅샷으로 보관.
 * 통화별(국내 KRW·해외 USD) 잔액을 각각 누적한다.
 */
@Service
@RequiredArgsConstructor
public class DepositService {

    private final DepositMapper depositMapper;

    /** 유저 KRW 예수금 잔액(없으면 0). */
    @Transactional(readOnly = true)
    public BigDecimal getKrwBalance(Long userId) {
        return getBalance(userId, "KRW");
    }

    /** 유저 예수금 잔액(통화별, 없으면 0). 해외(USD)·국내(KRW) 공통. */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId, String currency) {
        BigDecimal balance = depositMapper.findLatestBalance(userId, currency);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    /**
     * 예수금 원장에 1건 기록하고 기록 후 잔액을 반환.
     * @param signedAmount +입금(SELL/IN_TRANSFER) / −출금(BUY)
     */
    @Transactional
    public BigDecimal record(Long userId, Long accountId, String txType, BigDecimal signedAmount,
                             String currency, String refType, Long refId) {
        BigDecimal after = getBalance(userId, currency).add(signedAmount);
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
