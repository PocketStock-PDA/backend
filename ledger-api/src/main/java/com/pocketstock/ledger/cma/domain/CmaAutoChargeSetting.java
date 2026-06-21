package com.pocketstock.ledger.cma.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 부족금액 자동충전 설정(SETTLE-006) — 사용자당 1행.
 *
 * <p>CMA 원화 풀이 매수·정기적립 시점에 부족할 때, 연동 은행계좌에서 부족분만 on-demand로
 * 자동 보충할지를 켜고 끄는 설정. 실행(부족분 감지 → 교차 DB 자동이체)은 후속2 범위이며
 * 여기서는 설정 CRUD만 다룬다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CmaAutoChargeSetting {

    private Long id;
    private Long userId;
    private Boolean isEnabled;
    private Long sourceAccountRef;      // 충전 재원 = DB A linked_bank_accounts.id (교차 DB, FK 없음)
    private BigDecimal maxChargePerTx;  // 1회 충전 한도
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
