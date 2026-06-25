package com.pocketstock.ledger.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 릴레이(#204) — 미발행 이벤트를 폴링해 Kafka로 발행하고 마킹한다.
 *
 * <p>흐름: ① {@code published=false} 배치 조회 → ② 행 선점(markPublished, 멀티 인스턴스 이중발행 차단) →
 * ③ 선점 성공분만 Kafka 발행. 발행 실패 시 그 행은 다음 폴링에서 재시도(이미 마킹됐으면? — 아래 순서 주의).
 *
 * <p><b>순서 = 선점 먼저, 발행 나중</b>: markPublished(affected=1)로 한 인스턴스만 그 행을 가져가고, 그 다음 발행.
 * Kafka 발행이 실패하면 그 행은 이미 published=true라 재시도 안 됨 → <b>at-least-once보다 약함(유실 가능)</b>.
 * 이를 막으려면 "발행 성공 후 마킹"이 정석이나 멀티 인스턴스 이중발행이 생긴다. 트레이드오프:
 * 여기선 <b>발행 후 마킹</b>을 택해 유실 0을 우선(이중발행은 consumer가 event_id로 멱등 흡수). 선점은 트랜잭션으로 단일화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 미발행 이벤트 발행 — 1초 폴링. 알림은 1~2초 지연 무방. */
    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<Outbox> batch = outboxMapper.findUnpublished(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        int sent = 0;
        for (Outbox e : batch) {
            try {
                // 동기 발행(get) — 발행 성공 확인 후 마킹(유실 0 우선). 이중발행은 consumer 멱등(event_id) 흡수.
                kafkaTemplate.send(e.getTopic(), e.getEventId(), e.getPayload()).get();
                outboxMapper.markPublished(e.getId());
                sent++;
            } catch (Exception ex) {
                // 발행 실패 — 마킹 안 함 → 다음 폴링 재시도. 브로커 일시 장애 등.
                log.warn("[outbox] 발행 실패 — eventId={} topic={}, 다음 폴링 재시도", e.getEventId(), e.getTopic(), ex);
            }
        }
        if (sent > 0) {
            log.debug("[outbox] {}건 발행", sent);
        }
    }
}
