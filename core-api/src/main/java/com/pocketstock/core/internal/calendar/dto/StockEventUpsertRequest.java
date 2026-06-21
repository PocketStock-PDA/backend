package com.pocketstock.core.internal.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record StockEventUpsertRequest(
        @NotBlank String stockCode,
        @NotBlank String eventType,
        @NotNull  LocalDate eventDate,
        @NotBlank String title,
        String detail
) {}
