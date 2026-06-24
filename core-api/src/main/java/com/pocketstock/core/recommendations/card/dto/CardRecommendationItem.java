package com.pocketstock.core.recommendations.card.dto;

import java.math.BigDecimal;
import java.util.List;

public record CardRecommendationItem(
        String cardName,
        String cardType,
        String imageUrl,
        String applyUrl,
        BigDecimal annualFee,
        List<CardBenefitItem> benefits,
        int matchRate
) {}
