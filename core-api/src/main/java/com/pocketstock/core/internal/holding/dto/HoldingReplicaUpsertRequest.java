package com.pocketstock.core.internal.holding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * ledger → core 보유 복제 upsert 요청 한 건.
 * 증권 캘린더가 보유 종목으로 일정을 필터하는 근거({@code holdings_replica}).
 */
public record HoldingReplicaUpsertRequest(
        @NotNull Long userId,
        @NotBlank String stockCode,
        BigDecimal quantity,
        BigDecimal avgBuyPrice,
        String currency
) {}
