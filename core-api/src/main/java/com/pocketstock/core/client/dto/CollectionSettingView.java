package com.pocketstock.core.client.dto;

import java.math.BigDecimal;

/**
 * ledger collection_settings(DB B) 읽기 뷰 — 잔돈 스캔에서 끝전 임계값·활성 소스 판단에 사용.
 * threshold는 ACCOUNT 타입에만 의미(1000/5000/10000), null이면 기본 10000.
 */
public record CollectionSettingView(
        String sourceType,   // ACCOUNT / CARD / POINT
        Long sourceRefId,    // linked_bank_accounts / linked_cards / linked_points 의 id
        boolean enabled,
        BigDecimal threshold
) {}
