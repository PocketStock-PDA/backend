package com.pocketstock.core.budget.mapper;

import com.pocketstock.core.budget.dto.BudgetSavingsRow;
import com.pocketstock.core.budget.dto.CategorySavingsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BudgetSavingsMapper {

    List<CategorySavingsRow> findCategoryGoalsWithSpending(
            @Param("userId") Long userId,
            @Param("period") String period,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    BudgetSavingsRow findBudgetSavings(
            @Param("userId") Long userId,
            @Param("period") String period
    );

    void agreeCollect(
            @Param("userId") Long userId,
            @Param("period") String period
    );

    /** 절약금 모으기 동의 ON/OFF — 마이페이지 토글용(행 없으면 생성). */
    void setCollectAgreed(
            @Param("userId") Long userId,
            @Param("period") String period,
            @Param("agreed") boolean agreed
    );
}
