package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.DepositTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface DepositMapper {

    /** 예수금 잔액행 생성(계좌개설 시 balance=0). 계좌당 1행. */
    int insertBalance(@Param("accountId") Long accountId, @Param("currency") String currency);

    /**
     * 예수금 잔액 원자 갱신 — balance += delta, 출금 시 음수 가드.
     * @return 갱신된 행 수(0이면 잔액부족 또는 잔액행 없음)
     */
    int applyBalanceDelta(@Param("accountId") Long accountId, @Param("delta") BigDecimal delta);

    /** 계좌 예수금 현재잔액(account_balances). 없으면 null. */
    BigDecimal findBalanceByAccount(@Param("accountId") Long accountId);

    /** 예수금 거래 역사 INSERT (불변 journal, append-only) */
    int insert(DepositTransaction tx);
}
