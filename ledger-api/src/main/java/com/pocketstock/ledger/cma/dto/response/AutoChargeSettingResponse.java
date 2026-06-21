package com.pocketstock.ledger.cma.dto.response;

import com.pocketstock.ledger.cma.domain.CmaAutoChargeSetting;

import java.math.BigDecimal;

/**
 * 자동충전 설정 조회 응답. 설정 행이 없는 신규 사용자는 {@link #disabled()} 기본값을 반환한다.
 */
public record AutoChargeSettingResponse(
        boolean enabled,
        Long sourceAccountId,
        BigDecimal maxChargePerTx
) {

    /** 설정 미존재(신규 사용자) 기본값 — OFF. */
    public static AutoChargeSettingResponse disabled() {
        return new AutoChargeSettingResponse(false, null, null);
    }

    public static AutoChargeSettingResponse from(CmaAutoChargeSetting setting) {
        return new AutoChargeSettingResponse(
                Boolean.TRUE.equals(setting.getIsEnabled()),
                setting.getSourceAccountRef(),
                setting.getMaxChargePerTx());
    }
}
