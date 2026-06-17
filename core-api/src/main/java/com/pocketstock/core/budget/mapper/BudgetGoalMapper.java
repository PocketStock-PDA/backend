package com.pocketstock.core.budget.mapper;

import com.pocketstock.core.budget.dto.CategorySpendingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BudgetGoalMapper {

    List<CategorySpendingRow> findLastMonthCategorySpending(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    void upsertCategoryGoal(
            @Param("userId") Long userId,
            @Param("period") String period,
            @Param("category") String category,
            @Param("targetAmount") BigDecimal targetAmount
    );
}
