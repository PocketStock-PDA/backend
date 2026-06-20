package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.OperatingCashTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface OperatingCashMapper {

    /**
     * 회사 현금 잔액 원자 upsert — balance += delta. 통화행 없으면 생성(self-seeding).
     * ※ 유저 예수금과 달리 음수 가드 없음(회사 순현금은 음수 가능 = 정당한 회계값).
     * @return 갱신/생성된 행 수
     */
    int applyDelta(@Param("currency") String currency, @Param("delta") BigDecimal delta);

    /** 회사 현금 통화별 현재잔액(operating_cash_balances). 없으면 null. */
    BigDecimal findBalanceByCurrency(@Param("currency") String currency);

    /** 회사 현금 거래 역사 INSERT (불변 journal, append-only) */
    int insert(OperatingCashTransaction tx);
}
