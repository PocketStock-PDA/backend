package com.pocketstock.ledger.exchange.mapper;

import com.pocketstock.ledger.exchange.domain.FxTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FxTransactionMapper {

    /** 환전 기록 적재. 멱등키 충돌 시 기존 id 반환(ON DUPLICATE KEY). */
    int insert(FxTransaction tx);

    /** 멱등키로 기존 환전 1건 조회 — 재요청(replay) 시 기존 결과 반환용. 없으면 null. */
    FxTransaction findByIdempotencyKey(@Param("key") String key);

    List<FxTransaction> findByUser(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByUser(@Param("userId") Long userId);
}
