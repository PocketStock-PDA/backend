package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.trading.domain.DepositTransaction;
import com.pocketstock.ledger.trading.mapper.DepositMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 예수금 원장(append-only) 공통 처리. 잔액은 행마다 balance_after 스냅샷으로 보관.
 * ※ 현재 KRW(국내 위탁) 기준. 해외(USD)는 추후.
 */
@Service
@RequiredArgsConstructor
public class DepositService {

    private final DepositMapper depositMapper;

    /** 유저 KRW 예수금 잔액(없으면 0). */
    @Transactional(readOnly = true)
    public BigDecimal getKrwBalance(Long userId) {
        BigDecimal balance = depositMapper.findLatestKrwBalance(userId);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    /**
     * 예수금 원장에 1건 기록하고 기록 후 잔액을 반환.
     * @param signedAmount +입금(SELL/IN_TRANSFER) / −출금(BUY)
     */
    @Transactional
    public BigDecimal record(Long userId, Long accountId, String txType, BigDecimal signedAmount,
                             String currency, String refType, Long refId) {
        BigDecimal after = getKrwBalance(userId).add(signedAmount);
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
