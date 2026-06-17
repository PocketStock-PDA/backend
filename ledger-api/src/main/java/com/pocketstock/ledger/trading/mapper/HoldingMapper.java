package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.Holding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HoldingMapper {

    /** 계좌+종목 보유 1건 (없으면 null) */
    Holding findByAccountAndStock(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    /** 신규 보유 INSERT */
    int insert(Holding holding);

    /** 보유 수량·평단 갱신 */
    int updateQuantityAndAvg(@Param("id") Long id,
                             @Param("quantity") java.math.BigDecimal quantity,
                             @Param("avgBuyPrice") java.math.BigDecimal avgBuyPrice);

    /** 유저 보유종목 전체(수량>0) */
    List<Holding> findByUserId(@Param("userId") Long userId);
}
