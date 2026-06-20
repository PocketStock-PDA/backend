package com.pocketstock.core.recommendations.maturity.dto;

import java.util.List;

public record MaturityRecommendationResponse(
        TriggerAccountDto triggerAccount,
        List<DividendStockItem> recommendations
) {}
