package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.Allocation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AllocationMapper {

    /** 배분 INSERT (useGeneratedKeys → id 채움). append-only 배분 이력. */
    int insert(Allocation allocation);
}
