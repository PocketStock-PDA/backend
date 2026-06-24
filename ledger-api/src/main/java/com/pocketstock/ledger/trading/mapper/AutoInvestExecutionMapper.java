package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.AutoInvestExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AutoInvestExecutionMapper {

    /** 회차 로그 적재. (auto_invest_stock_id, round_no) UNIQUE — 중복 시 DuplicateKey. */
    int insert(AutoInvestExecution execution);

    /** 종목의 현재 최대 회차(없으면 null) — 다음 회차 = max+1. */
    Integer findMaxRoundNo(@Param("autoInvestStockId") Long autoInvestStockId);

    /** 종목별 모으기 내역(회차 desc) — #195 조회 API용. */
    List<AutoInvestExecution> findByStock(@Param("autoInvestStockId") Long autoInvestStockId);
}
