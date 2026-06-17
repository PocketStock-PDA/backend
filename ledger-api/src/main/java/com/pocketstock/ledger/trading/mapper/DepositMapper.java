package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.DepositTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface DepositMapper {

    /** 유저 KRW 예수금 최신 잔액(deposit_transactions.balance_after). 없으면 null. */
    BigDecimal findLatestKrwBalance(@Param("userId") Long userId);

    /** 예수금 원장 INSERT (append-only) */
    int insert(DepositTransaction tx);
}
