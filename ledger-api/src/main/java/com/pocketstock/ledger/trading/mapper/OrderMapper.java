package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    /** 주문 INSERT (useGeneratedKeys → id 채움) */
    int insert(Order order);

    /** 멱등키(client_order_id)로 기존 주문 조회 — 재요청 단락용. 없으면 null. */
    Order findByClientOrderId(@Param("clientOrderId") String clientOrderId);

    /** 주문 상태·체결가 갱신 (RECEIVED → FILLED) */
    int updateFill(@Param("id") Long id, @Param("status") String status, @Param("price") java.math.BigDecimal price);

    /** 유저 거래내역(최신순) */
    List<Order> findByUserId(@Param("userId") Long userId);
}
