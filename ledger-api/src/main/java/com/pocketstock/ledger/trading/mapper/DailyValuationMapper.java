package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.DailyValuation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyValuationMapper {

    /** 일별 스냅샷 upsert — (user_id, stock_code, eval_date) UNIQUE라 같은 날 재실행 시 갱신(멱등). */
    int upsert(DailyValuation valuation);

    /** 종목 수익률 추이(기간) — eval_date asc. 차트용. */
    List<DailyValuation> findByUserAndStock(@Param("userId") Long userId,
                                            @Param("stockCode") String stockCode,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);
}
