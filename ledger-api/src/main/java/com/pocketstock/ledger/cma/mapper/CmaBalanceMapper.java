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
}
