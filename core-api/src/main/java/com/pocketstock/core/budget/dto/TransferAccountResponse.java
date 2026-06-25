package com.pocketstock.core.budget.dto;

public record TransferAccountResponse(
        Long accountId,
        String bankName,
        String accountName
) {}
