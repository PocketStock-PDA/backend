package com.pocketstock.core.recommendations.card.dto;

import java.util.List;

public record CardRecommendationResponse(
        List<TopCategory> topCategories,
        List<CardRecommendationItem> recommendations
) {}
