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
    private BigDecimal perShareDividend;   // 주당 현금배당금(KRW) — 가장 가까운 DIVIDEND_PAY, 없으면 null
    private LocalDate payDate;             // 배당금 지급일 — 가장 가까운 DIVIDEND_PAY, 없으면 null
}
