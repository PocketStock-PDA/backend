package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.AssetCategoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssetSummaryMapper {

    /** 연동 은행 계좌를 자산 유형(account_type)별로 집계 */
    List<AssetCategoryRow> findBankAssetsByCategory(@Param("userId") Long userId);

    /** 타사 보유 종목 평가금액 합계 */
    BigDecimal sumExternalHoldings(@Param("userId") Long userId);

    /** 고정비/변동비 분류별 지출 합계 */
    List<AssetCategoryRow> findSpendingByType(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
