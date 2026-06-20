package com.pocketstock.core.recommendations.maturity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DividendStockRow {
    private String stockCode;
    private String stockName;
    private String category;
    private BigDecimal dividendYield;
    private String tags;
    private LocalDate exDividendDate;
}
