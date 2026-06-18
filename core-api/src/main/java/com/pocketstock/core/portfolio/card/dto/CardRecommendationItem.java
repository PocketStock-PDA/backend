package com.pocketstock.core.portfolio.card.dto;

import java.util.List;

public record CardRecommendationItem(
        String cardName,
        String cardCompany,
        Integer annualFee,
        List<String> benefits,
        int matchRate
) {}
