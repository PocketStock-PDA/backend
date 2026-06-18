package com.pocketstock.ledger.cma.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CollectionSettingRequest(
        @Valid @NotNull List<SettingItem> settings
) {
    public record SettingItem(
            @NotNull String sourceType,
            Long sourceRefId,
            @NotNull Boolean enabled,
            Integer threshold   // 끝전 커팅 기준: 1000 / 5000 / 10000, null이면 기존값 유지
    ) {}
}
