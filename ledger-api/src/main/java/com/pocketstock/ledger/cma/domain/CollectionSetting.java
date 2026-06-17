package com.pocketstock.ledger.cma.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
