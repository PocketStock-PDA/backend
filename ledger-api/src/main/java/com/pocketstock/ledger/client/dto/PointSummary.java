package com.pocketstock.ledger.client.dto;

import java.math.BigDecimal;

public record PointSummary(
        Long linkedAccountId,
        BigDecimal availablePoints
) {}
