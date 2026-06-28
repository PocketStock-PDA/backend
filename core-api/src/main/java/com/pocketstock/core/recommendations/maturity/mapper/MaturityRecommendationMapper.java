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

    /** 만기 굴리기 대상 예적금 전체(미래 만기) — 만기 임박 순. 선택 화면용. */
    List<TriggerAccountRow> findMaturityAccounts(@Param("userId") Long userId);

    /** 선택한 예적금 1건(소유·예적금·미래 만기 검증 포함) — 계좌 지정 추천용. 없으면 null. */
    TriggerAccountRow findMaturityAccountById(@Param("userId") Long userId,
                                             @Param("accountId") Long accountId);

    List<DividendStockRow> findDividendStocksAboveRate(@Param("interestRatePct") BigDecimal interestRatePct);
}
