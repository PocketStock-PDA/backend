package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 웰컴 보상 지급 이력 (welcome_rewards, DB B 원장). user_id UNIQUE = 1인 1회.
 * 온보딩(계좌개설+연동) 완료 후 후보 4종목 중 1개를 골라 1,000원어치 소수점 주식을
 * 무상 지급한 기록. holdings 적립과 같은 로컬 트랜잭션으로 남긴다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WelcomeReward {

    private Long id;
    private Long userId;
    private Long accountId;
    private String stockCode;
    private String market;          // DOMESTIC | OVERSEAS
    private BigDecimal quantity;    // 지급 소수점 수량
    private BigDecimal grantPrice;  // 지급시점 현재가(종목통화)
    private Integer budgetKrw;      // 지급 예산(원) = 1000
    private String currency;        // KRW | USD
    private LocalDateTime grantedAt;
}
