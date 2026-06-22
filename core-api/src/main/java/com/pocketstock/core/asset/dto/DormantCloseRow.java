package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 휴면계좌 해지 검증·분류용 매퍼 행(본인 소유 계좌). 화면 노출 DTO 아님.
 * {@code closedAt != null} = 이미 소프트 해지된 계좌(ALREADY_CLOSED 판정),
 * {@code isDormant && closedAt == null} = 해지 대상.
 */
public record DormantCloseRow(
        Long accountId,
        BigDecimal balance,
        String currency,
        Boolean isDormant,
        LocalDateTime closedAt,
        BigDecimal closedAmount
) {
}
