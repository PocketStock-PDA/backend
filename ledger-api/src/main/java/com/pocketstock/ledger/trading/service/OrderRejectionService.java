package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.OrderStatus;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 실패 주문을 REJECTED로 별도 트랜잭션에 기록(감사 추적, H3).
 * 본 체결 @Transactional 의 롤백과 무관하게 REJECTED 행만 독립 commit 되도록 REQUIRES_NEW.
 * - client_order_id = NULL → 멱등키를 소비하지 않음(자금이 롤백으로 환원되므로 동일 키 재시도 허용).
 * - 자금 환원은 본 tx 롤백이 자동 처리(온주 단일 tx). 여기선 감사 행만 남긴다.
 * ※ account_id·(stock_code,exchange) FK가 유효해야 INSERT 가능 → 종목·계좌 해석 이후의 비즈니스 실패에만 기록.
 */
@Service
@RequiredArgsConstructor
public class OrderRejectionService {

    private final OrderMapper orderMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(Long userId, Long accountId, String stockCode, String exchange,
                                String side, String orderType, BigDecimal quantity, BigDecimal price,
                                String currency, String reason) {
        Order rejected = Order.builder()
                .clientOrderId(null)               // 멱등키 비소비
                .userId(userId)
                .accountId(accountId)
                .stockCode(stockCode)
                .exchange(exchange)
                .side(side)
                .orderType(orderType)
                .orderQuantity(quantity)
                .price(price)
                .status(OrderStatus.REJECTED)
                .source("MANUAL")
                .currency(currency)
                .failReason(reason == null ? "" : (reason.length() > 255 ? reason.substring(0, 255) : reason))
                .requestedAt(LocalDateTime.now())
                .build();
        orderMapper.insert(rejected);
    }
}
