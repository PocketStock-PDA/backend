package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssetSummaryResponse(
        BigDecimal netAssets,
        BigDecimal momDiff,
        String peerAgeGroup,
        int peerRankPercent,
        List<AssetPortfolioItem> portfolio,
        BigDecimal fixedExpenses,
        BigDecimal variableExpenses
) {}
