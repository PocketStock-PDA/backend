package com.pocketstock.ledger.trading.dto;

import java.util.List;

/**
 * 증권계좌 개설 응답. accountNo = 대표 계좌번호(요청한 첫 시장 기준).
 */
public record OpenAccountResponse(String accountNo, List<String> accountTypes) {
}
