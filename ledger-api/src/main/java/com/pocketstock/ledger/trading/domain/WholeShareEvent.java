package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 온주 전환 이벤트 (whole_share_events, DB B 원장, FRAC-010 #157).
 * 소수점(신탁) 보유가 1주↑일 때 사용자가 전환 버튼을 누르면 정수부를 온주(직접소유)로 굳히고 1행 기록.
 * holdings.quantity는 불변(온주=quantity−fractional_qty가 +wholeQty). 퍼즐·축하 알림 트리거 기반.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WholeShareEvent {

    private Long id;
    private Long userId;
    private Long accountId;
    private String stockCode;
    private Integer wholeQty;          // 전환된 온주 수
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;
    private String stockName;          // 조회 join 전용(테이블 컬럼 아님)
}
