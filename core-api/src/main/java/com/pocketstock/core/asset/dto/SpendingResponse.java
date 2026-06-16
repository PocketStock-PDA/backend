package com.pocketstock.core.asset.dto;

import java.util.List;

public record SpendingResponse(long totalSpending, List<CategorySpending> categories) {}
