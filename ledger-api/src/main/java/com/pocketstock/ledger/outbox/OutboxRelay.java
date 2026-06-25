package com.pocketstock.ledger.outbox;

import com.pocketstock.ledger.lifecycle.LedgerActivation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 릴레이(#204) — 미발행 이벤트를 폴링해 Kafka로 발행하고 마킹한다.
 *
 * <p>흐름: ① active ledger 색만 {@code published=false} 배치 조회 → ② Kafka 발행 →
 * ③ 발행 성공분만 published 마킹. 발행 실패 시 그 행은 다음 폴링에서 재시도한다.
 *
 * <p>순서는 <b>발행 후 마킹</b>이다. Kafka 장애 시 published=false로 남겨 복구 후 재시도하고,
 * blue-green 중복 실행은 {@link LedgerActivation}으로 줄인다. 만약 중복 발행이 발생해도 consumer가 event_id로 멱등 흡수한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LedgerActivation activation;

    /** 미발행 이벤트 발행 — active ledger 색에서만 1초 폴링. 알림은 1~2초 지연 무방. */
    @Scheduled(fixedDelay = 1000)
    public void relay() {
        if (!activation.isActive()) {
            return;
        }
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
