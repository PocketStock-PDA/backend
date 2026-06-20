package com.pocketstock.core.budget.dto;

import java.util.List;

public record ComparisonResponse(
        String currentPeriod,
        String lastPeriod,
        List<ComparisonItem> categories
) {}
