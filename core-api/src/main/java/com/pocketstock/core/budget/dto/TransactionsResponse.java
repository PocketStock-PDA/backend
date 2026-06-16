package com.pocketstock.core.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionsResponse(List<TransactionItem> transactions, BigDecimal totalAmount) {}
