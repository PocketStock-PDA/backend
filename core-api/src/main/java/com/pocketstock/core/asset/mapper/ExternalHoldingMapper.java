package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.ExternalHoldingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExternalHoldingMapper {

    /** 타사 보유 종목 평면 조회(증권사 회사명 해석 포함). Service에서 회사 단위로 그룹. */
    List<ExternalHoldingRow> findExternalHoldings(@Param("userId") Long userId);
}
