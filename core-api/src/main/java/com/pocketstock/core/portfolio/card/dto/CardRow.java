package com.pocketstock.core.portfolio.card.dto;

import lombok.Data;

@Data
public class CardRow {
    private Long id;
    private String cardName;
    private String cardType;
    private String provider;
    private Integer annualFeeDomestic;
}
