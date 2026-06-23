package com.pocketstock.core.asset.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetCategoryRow {
    private String category;
    private BigDecimal amount;
}
