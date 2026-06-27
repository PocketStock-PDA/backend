package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.Allocation;
import com.pocketstock.ledger.trading.dto.OrderFilledAmount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AllocationMapper {

    /** 배분 INSERT (useGeneratedKeys → id 채움). append-only 배분 이력. */
    int insert(Allocation allocation);

    /** 주문 id별 체결금액(gross_amount) 합 — 소수점 체결분 거래내역 금액용. 배분 없는 주문은 결과에서 빠짐. */
    List<OrderFilledAmount> sumGrossByOrderIds(@Param("orderIds") List<Long> orderIds);
}
