package com.pocketstock.core.budget.dto;

import jakarta.validation.constraints.NotNull;

public record SetTransferAccountRequest(@NotNull Long accountId) {}
