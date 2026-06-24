package com.pocketstock.core.recommendations.card.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CardRow {
    private Long id;
    private String cardName;
    private String cardType;
    private String provider;
    private BigDecimal annualFeeDomestic;
    private String imageUrl;
    private String applyUrl;
}
