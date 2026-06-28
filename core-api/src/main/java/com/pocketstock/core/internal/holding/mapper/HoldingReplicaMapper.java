package com.pocketstock.core.internal.holding.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface HoldingReplicaMapper {

    /** 보유 복제 1건 upsert — UNIQUE(user_id, stock_code) 충돌 시 수량·평단·통화·동기화시각 갱신. 비파괴. */
    void upsertReplica(@Param("userId") Long userId,
                       @Param("stockCode") String stockCode,
                       @Param("quantity") BigDecimal quantity,
                       @Param("avgBuyPrice") BigDecimal avgBuyPrice,
                       @Param("currency") String currency);
}
