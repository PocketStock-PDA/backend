package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;

/**
 * 내부 호출(core 휴면계좌 해지) 입금 결과 — 반영 후 CMA 통화풀 잔액.
 * 멱등 재호출 시에도 기존 {@code balance_after}가 그대로 반환된다.
 */
public record CmaCreditResponse(BigDecimal balanceAfter) {
}
