package com.pocketstock.core.internal.asset.dto;

import java.math.BigDecimal;

public record PointSummary(
        Long linkedAccountId,
        BigDecimal availablePoints
) {}
