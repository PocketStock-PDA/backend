package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

/**
 * 잔돈 수집 확정 시 core-api(DB A)의 원천 잔액에서 차감할 금액.
 * {@code id} = 출처 레코드 식별자(연동 계좌/포인트), {@code amount} = 이번에 수집된 금액(양수).
 */
public record SourceDeduction(
        Long id,
        BigDecimal amount
) {
    // 차감 계약 불변식 강제 — 잘못된(null/음수/0) 값이 Feign 호출까지 전파되지 않도록 생성 시점에 차단.
    public SourceDeduction {
        if (id == null) {
            throw new IllegalArgumentException("SourceDeduction.id는 필수입니다.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("SourceDeduction.amount는 0보다 커야 합니다. (id=" + id + ")");
        }
    }
}
