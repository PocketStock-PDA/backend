package com.pocketstock.core.portfolio.card.dto;

import java.math.BigDecimal;
import java.util.List;

public record CardRecommendationItem(
        String cardName,
        String cardCompany,
        BigDecimal annualFee,
        List<String> benefits,
        int matchRate
) {}
