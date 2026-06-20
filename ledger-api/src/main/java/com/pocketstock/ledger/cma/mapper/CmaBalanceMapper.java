package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CmaBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CmaBalanceMapper {

    List<CmaBalance> findByAccountId(@Param("cmaAccountId") Long cmaAccountId);

    CmaBalance findByAccountIdAndCurrency(@Param("cmaAccountId") Long cmaAccountId,
                                          @Param("currency") String currency);

    int upsertBalance(CmaBalance balance);

    /** 신규 계좌 지갑 시드 — 이미 있으면 무시(INSERT IGNORE)해 기존 잔액을 덮어쓰지 않음 */
    int insertBalance(CmaBalance balance);
}
