package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CmaTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CmaTransactionMapper {

    int insert(CmaTransaction tx);

    /** 멱등키로 기존 원장행 조회 — 재호출(replay) 시 잔액 재반영 없이 기존 balance_after를 돌려주기 위함 */
    CmaTransaction findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    List<CmaTransaction> findByUserIdAndFilter(
            @Param("userId") Long userId,
            @Param("txType") String txType,
            @Param("sourceType") String sourceType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<CmaTransaction> findCollectHistory(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<CmaTransaction> findTransfers(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    // 이번 달 카드 라운드업 누적 수집액 — 홈 "수집한 잔돈"은 카드만 노출. 수집 0건이면 0.
    BigDecimal sumCardCollectedThisMonth(@Param("userId") Long userId);
}
