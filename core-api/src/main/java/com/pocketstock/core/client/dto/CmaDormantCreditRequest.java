package com.pocketstock.core.client.dto;

import java.math.BigDecimal;

/**
 * core→ledger 휴면계좌 해지 입금 요청 — {@code POST /internal/cma/credit} 본문.
 * ledger의 {@code InternalCmaCreditRequest}와 필드가 1:1 대응한다(거래유형·멱등키는 ledger가 강제).
 */
public record CmaDormantCreditRequest(
        Long userId,
        Long accountId,
        BigDecimal amount,
        String currency
) {
}
