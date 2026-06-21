package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 온주 매수/매도 요청. 호가창 기반.
 * - clientOrderId: 클라가 발급하는 멱등키(FIX clOrdID). 따닥 탭·네트워크 재전송 시 같은 값을 보내면
 *   서버가 중복 체결 없이 기존 주문 결과를 반환한다(#90). 요청마다 새로 만들지 말고 "주문 1건당 1개" 유지.
 * - orderType=LIMIT: price(지정가, 호가 탭한 가격) 필수
 * - orderType=MARKET: price 무시, 최우선 호가로 체결
 * - quantity: 정수 주수
 */
public record WholeOrderRequest(
        String clientOrderId, // 멱등키(클라 발급)
        String stockCode,
        String side,        // BUY | SELL
        String orderType,   // LIMIT | MARKET
        BigDecimal price,   // LIMIT일 때 지정가
        long quantity       // 정수 주수
) {
}
