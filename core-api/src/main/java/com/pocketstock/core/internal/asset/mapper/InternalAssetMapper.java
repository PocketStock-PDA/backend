package com.pocketstock.core.internal.asset.mapper;

import com.pocketstock.core.internal.asset.dto.LinkedAccountSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface InternalAssetMapper {

    List<LinkedAccountSummary> findLinkedAccountsByUserAndIds(
            @Param("userId") Long userId,
            @Param("ids") List<Long> ids
    );

    // id, amount 두 컬럼만 반환 — 서비스에서 라운드업 계산
    List<Map<String, Object>> findUncollectedCardTxs(
            @Param("userId") Long userId,
            @Param("linkedAccountId") Long linkedAccountId
    );

    int markRoundupCollected(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    BigDecimal findPointBalance(
            @Param("userId") Long userId,
            @Param("linkedAccountId") Long linkedAccountId
    );
}
