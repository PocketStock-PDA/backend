package com.pocketstock.ledger.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Transactional Outbox 행(#204) — 비동기 알림 이벤트를 체결·자동모으기 트랜잭션과 같은 커밋에 적어둔다.
 * 릴레이({@link OutboxRelay})가 미발행분을 폴링해 Kafka로 발행 → published=true 마킹.
 * 이중쓰기(체결 커밋 후 Kafka 발행 실패=알림 유실) 차단. event_id로 consumer 멱등.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Outbox {

    private Long id;
    private String eventId;        // 멱등키 — order:{id}:filled / autoinvest:exec:{id}
    private String topic;          // trading.order.filled / autoinvest.executed
    private String aggregateType;  // ORDER / AUTO_INVEST
    private Long aggregateId;
    private String payload;        // JSON 직렬화 본문
    private Boolean published;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
