package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;

public record AssetPortfolioItem(
        String category,
        BigDecimal amount,
        BigDecimal ratio
) {}
