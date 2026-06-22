package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

/**
 * 잔돈 수집 확정 시 core-api(DB A)의 원천 잔액에서 차감할 금액.
 * {@code id} = 출처 레코드 식별자(연동 계좌/포인트), {@code amount} = 이번에 수집된 금액(양수).
 */
public record SourceDeduction(
        Long id,
        BigDecimal amount
) {}
