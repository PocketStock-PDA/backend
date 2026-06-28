package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.AssetCategoryRow;
import com.pocketstock.core.asset.dto.PointSource;
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

    /** 연동 포인트를 출처(point_name)별 개별 행으로 조회(1P=1원). 합계는 서비스에서 파생. */
    List<PointSource> findPoints(@Param("userId") Long userId);

    /** 고정비/변동비 분류별 지출 합계 */
    List<AssetCategoryRow> findSpendingByType(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
