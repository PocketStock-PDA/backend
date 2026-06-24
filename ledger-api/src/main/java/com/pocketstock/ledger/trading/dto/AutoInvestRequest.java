package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 자동모으기 등록/수정 요청 (주기 base). 트리거(물타기/익절)는 별도 엔드포인트.
 * - period=DAILY: periodDay 무시 / WEEKLY: periodDay 요일 1~5(월~금) / MONTHLY: periodDay 1~31
 * - amountType=AMOUNT: buyAmount 필수(국내 ≥1,000원·천원단위 / 해외 ≥$0.01) / QUANTITY: buyQuantity 필수
 * ※ market·currency·accountId는 stockCode→exchange에서 파생(요청에 받지 않음).
 */
public record AutoInvestRequest(
        String stockCode,
        String period,        // DAILY | WEEKLY | MONTHLY
        Integer periodDay,    // WEEKLY 1~5 / MONTHLY 1~31 / DAILY null
        String amountType,    // AMOUNT | QUANTITY
        BigDecimal buyAmount,
        BigDecimal buyQuantity
) {
}
