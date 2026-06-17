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
public class CmaBalance {

    private Long id;
    private Long cmaAccountId;
    private String currency;        // KRW / USD
    private BigDecimal balance;
    private BigDecimal interestRate;
    private LocalDateTime updatedAt;
}
