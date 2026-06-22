package com.pocketstock.ledger.cma.dto.response;

import com.pocketstock.ledger.cma.domain.CollectionSetting;

import java.math.BigDecimal;

/**
 * 내부 호출(core 잔돈 스캔)용 collection_settings 뷰 — 화면 노출 DTO 아님.
 * core가 끝전 임계값·활성 소스만 알면 되므로 id·타임스탬프는 제외.
 */
public record CollectionSettingView(
        String sourceType,
        Long sourceRefId,
        boolean enabled,
        BigDecimal threshold
) {
    public static CollectionSettingView from(CollectionSetting s) {
        return new CollectionSettingView(
                s.getSourceType(),
                s.getSourceRefId(),
                Boolean.TRUE.equals(s.getIsEnabled()),
                s.getThreshold()
        );
    }
}
