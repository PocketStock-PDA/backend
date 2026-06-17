package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.CategoryAmountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SpendingMapper {

    List<CategoryAmountRow> findCategorySpending(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
