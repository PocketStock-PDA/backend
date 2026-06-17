package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CmaTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CmaTransactionMapper {

    int insert(CmaTransaction tx);

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

    BigDecimal sumCollectedToday(@Param("userId") Long userId);
}
