package com.pocketstock.core.budget.mapper;

import com.pocketstock.core.budget.dto.TransferAccountResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BudgetTransferMapper {

    TransferAccountResponse findTransferAccount(@Param("userId") Long userId);

    boolean existsAccountOwnedBy(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId
    );

    void upsertTransferAccount(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId
    );
}
