package com.pocketstock.core.budget.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionRow {
    private Long transactionId;
    private String category;
    private String description;
    private BigDecimal amount;
    private LocalDateTime transactedAt;
}
