package com.pocketstock.core.internal.asset.dto;

import java.math.BigDecimal;
import java.util.List;

public record CardRoundupSummary(
        BigDecimal totalRoundupAmount,
        List<Long> cardTransactionIds
) {}
