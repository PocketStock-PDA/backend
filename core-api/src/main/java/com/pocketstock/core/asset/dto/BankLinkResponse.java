package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;

/** 은행 개별 연동 응답 — 연동된(또는 기존) 대표 계좌. */
public record BankLinkResponse(Long accountId, BigDecimal balance) {
}
