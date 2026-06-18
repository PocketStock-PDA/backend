package com.pocketstock.core.portfolio.card.dto;

import lombok.Data;

@Data
public class CardBenefitRow {
    private Long cardId;
    private String benefitCategory;
    private String benefitDesc;
}
