package com.pocketstock.core.recommendations.card.mapper;

import com.pocketstock.core.asset.dto.CategoryAmountRow;
import com.pocketstock.core.recommendations.card.dto.CardBenefitRow;
import com.pocketstock.core.recommendations.card.dto.CardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CardMapper {

    List<CategoryAmountRow> findCategorySpending(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    List<CardRow> findAllActiveCards();

    List<CardBenefitRow> findAllBenefits();
}
