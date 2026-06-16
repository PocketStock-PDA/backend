package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    /** 주문 INSERT (useGeneratedKeys → id 채움) */
    int insert(Order order);

    /** 주문 상태·체결가 갱신 (RECEIVED → FILLED) */
    int updateFill(@Param("id") Long id, @Param("status") String status, @Param("price") java.math.BigDecimal price);

    /** 유저 거래내역(최신순) */
    List<Order> findByUserId(@Param("userId") Long userId);
}
