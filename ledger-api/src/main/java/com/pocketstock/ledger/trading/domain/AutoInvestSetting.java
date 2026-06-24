package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 자동모으기 전역 설정 (auto_invest_settings, DB B, 1인 1행 마스터 스위치).
 * 실제 주기·금액은 종목별(auto_invest_stocks), 트리거는 auto_invest_triggers. 여기는 전체 ON/OFF·일시중지만.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoInvestSetting {

    private Long id;
    private Long userId;
    private Boolean isEnabled;             // 자동모으기 전체 ON/OFF
    private Boolean isPaused;              // 일시중지
    private Boolean keepCollectingOnPause; // 중지 중 잔돈 적립은 유지할지
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
