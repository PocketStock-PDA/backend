package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.BatchOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface BatchOrderMapper {

    /** 블록주문 INSERT (useGeneratedKeys → id 채움). */
    int insert(BatchOrder batchOrder);

    /** 시뮬 체결 확정 — 체결가·시각 기록 + status FILLED. */
    int markFilled(@Param("id") Long id,
                   @Param("fillPrice") BigDecimal fillPrice,
                   @Param("filledAt") LocalDateTime filledAt);
}
