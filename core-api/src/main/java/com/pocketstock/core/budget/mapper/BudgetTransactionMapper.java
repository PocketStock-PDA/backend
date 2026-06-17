package com.pocketstock.core.budget.mapper;

import com.pocketstock.core.budget.dto.TransactionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BudgetTransactionMapper {

    List<TransactionRow> findTransactions(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
