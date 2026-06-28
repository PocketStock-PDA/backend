package com.pocketstock.core.recommendations.maturity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DividendStockItem(
        String stockCode,
        String stockName,
        String category,
        String market,
        BigDecimal dividendYield,
        List<String> tags,
        LocalDate exDividendDate,
        BigDecimal perShareDividend,
        LocalDate payDate,
        String reason
) {}
