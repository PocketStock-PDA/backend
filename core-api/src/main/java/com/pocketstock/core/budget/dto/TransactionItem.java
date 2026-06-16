package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionItem(
        Long transactionId,
        String category,
        String description,
        BigDecimal amount,
        LocalDateTime transactedAt
) {}
