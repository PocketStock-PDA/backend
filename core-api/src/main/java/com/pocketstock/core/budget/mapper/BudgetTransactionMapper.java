package com.pocketstock.core.budget.mapper;

import com.pocketstock.core.budget.dto.TransactionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BudgetTransactionMapper {

    List<TransactionRow> findTransactions(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("day") Integer day
    );
}
