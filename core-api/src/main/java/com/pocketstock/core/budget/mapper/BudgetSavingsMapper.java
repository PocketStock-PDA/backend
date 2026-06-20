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
}
