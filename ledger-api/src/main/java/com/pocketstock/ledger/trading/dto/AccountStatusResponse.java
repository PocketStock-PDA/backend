package com.pocketstock.ledger.trading.dto;

/**
 * 계좌 상태 조회 응답 항목. type = DOMESTIC | OVERSEAS.
 */
public record AccountStatusResponse(String type, String accountNo, String status) {
}
