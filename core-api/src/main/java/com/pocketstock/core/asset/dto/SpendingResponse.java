package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;
import java.util.List;

public record SpendingResponse(BigDecimal totalSpending, List<CategorySpending> categories) {}
