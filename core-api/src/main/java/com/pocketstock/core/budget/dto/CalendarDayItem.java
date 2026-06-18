package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;

public record CalendarDayItem(
        String date,
        BigDecimal spent,
        String status
) {}
