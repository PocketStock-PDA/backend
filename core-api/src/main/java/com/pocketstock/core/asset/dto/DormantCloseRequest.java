package com.pocketstock.core.asset.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 휴면계좌 일괄 해지 요청 — 다중 체크 선택된 계좌 식별자.
 * 목적지(CMA)는 고정이라 {@code targetAccount} 없음. 비었거나 중복된 id는 거부(서비스에서 중복 검사).
 */
public record DormantCloseRequest(
        @NotEmpty(message = "해지할 휴면계좌를 선택해 주세요.")
        List<Long> accountIds
) {
}
