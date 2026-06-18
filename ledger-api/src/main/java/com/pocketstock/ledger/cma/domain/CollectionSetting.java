package com.pocketstock.ledger.cma.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CollectionSetting {

    private Long id;
    private Long userId;
    private String sourceType;      // ACCOUNT / CARD / POINT
    private Long sourceRefId;       // linked_account_id 등
    private Boolean isEnabled;
    private BigDecimal threshold;        // 끝전 커팅 기준: 1000 / 5000 / 10000 (ACCOUNT 타입에만 적용)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
