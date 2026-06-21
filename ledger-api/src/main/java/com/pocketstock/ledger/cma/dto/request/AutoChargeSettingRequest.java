package com.pocketstock.ledger.cma.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 자동충전 설정 변경 요청.
 *
 * <p>{@code enabled=true}이면 {@code sourceAccountId} 필수 + {@code maxChargePerTx > 0},
 * {@code enabled=false}이면 두 필드 null 허용(끄기). 이 조건부 검증은 서비스에서 수행한다.
 */
public record AutoChargeSettingRequest(
        @NotNull Boolean enabled,
        Long sourceAccountId,       // 충전 재원 = DB A linked_bank_accounts.id
        BigDecimal maxChargePerTx
) {}
