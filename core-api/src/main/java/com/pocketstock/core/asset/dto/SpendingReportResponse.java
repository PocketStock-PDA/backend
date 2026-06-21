package com.pocketstock.core.asset.dto;

public record SpendingReportResponse(
        String period,
        String insight,
        String topCategory,
        String savingTip
) {}
