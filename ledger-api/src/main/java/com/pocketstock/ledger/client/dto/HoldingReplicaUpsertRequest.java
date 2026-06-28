package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

/** ledger → core 보유 복제 upsert 요청 한 건 (core-api {@code /internal/holdings/replica} 바디). */
public record HoldingReplicaUpsertRequest(
        Long userId,
        String stockCode,
        BigDecimal quantity,
        BigDecimal avgBuyPrice,
        String currency
) {}
