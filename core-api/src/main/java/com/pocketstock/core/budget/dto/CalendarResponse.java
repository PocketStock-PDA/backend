package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record CalendarResponse(
        int year,
        int month,
        BigDecimal dailyBudget,
        List<CalendarDayItem> days
) {}
