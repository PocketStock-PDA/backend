package com.pocketstock.core.client.dto;

import java.math.BigDecimal;

/** ledger 휴면계좌 해지 입금 응답 뷰 — 반영 후 CMA 통화풀 잔액. */
public record CmaCreditView(BigDecimal balanceAfter) {
}
