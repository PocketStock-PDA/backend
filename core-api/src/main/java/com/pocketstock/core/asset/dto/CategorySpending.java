package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;

public record CategorySpending(String category, BigDecimal amount, BigDecimal ratio) {}
