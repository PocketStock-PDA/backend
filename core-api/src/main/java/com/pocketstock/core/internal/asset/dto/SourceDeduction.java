package com.pocketstock.core.internal.asset.dto;

import java.math.BigDecimal;

/**
 * 잔돈 수집 확정 시 원천(연동 계좌/포인트)에서 실제로 차감할 금액.
 *
 * <p>{@code id}는 출처 레코드 식별자(linked_bank_accounts.id / linked_points.id),
 * {@code amount}는 이번 수집으로 빠져나간 금액(양수). ledger-api가 원장 입금을 기록한 뒤
 * Feign으로 전달해, 같은 끝전·포인트가 다시 수집되지 않도록 원천을 닫는다(CARD의
 * mark-collected와 동일한 역할).
 */
public record SourceDeduction(
        Long id,
        BigDecimal amount
) {}
