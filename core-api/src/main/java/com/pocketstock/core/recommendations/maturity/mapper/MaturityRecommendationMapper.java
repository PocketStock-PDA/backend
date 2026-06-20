package com.pocketstock.core.recommendations.maturity.mapper;

import com.pocketstock.core.recommendations.maturity.dto.DividendStockRow;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface MaturityRecommendationMapper {

    TriggerAccountRow findUpcomingMaturityAccount(@Param("userId") Long userId);

    List<DividendStockRow> findDividendStocksAboveRate(@Param("interestRatePct") BigDecimal interestRatePct);
}
