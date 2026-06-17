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
public class CmaAccount {

    private Long id;
    private Long userId;
    private byte[] accountNoEnc;
    private String status;          // ACTIVE / SUSPENDED / CLOSED
    private LocalDateTime openedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
