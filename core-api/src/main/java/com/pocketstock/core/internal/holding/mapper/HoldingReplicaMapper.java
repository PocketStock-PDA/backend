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

    /** 보유 복제 1건 삭제 — 전량 매도 시 호출(안 가진 종목 일정이 캘린더에 남지 않도록). @return 삭제 행 수. */
    int deleteReplica(@Param("userId") Long userId, @Param("stockCode") String stockCode);
}
