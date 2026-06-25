package com.pocketstock.ledger.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 발행원(체결·자동모으기)이 호출하는 outbox 기록 헬퍼(#204). 발행원의 {@code @Transactional} 안에서 호출돼
 * 체결과 <b>같은 커밋</b>에 이벤트를 남긴다 → 이중쓰기 차단. 실제 Kafka 발행은 {@link OutboxRelay}가 비동기로.
 *
 * <p>event_id는 발행원이 결정적으로 만든다(예 {@code order:1234:filled}) → 같은 사건 재실행 시 DuplicateKey로
 * 중복 기록을 멱등 흡수(이미 기록됨 = 무시). payload는 임의 객체를 JSON 직렬화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * 이벤트 기록 — 호출자 트랜잭션에 합류. 같은 event_id가 이미 있으면 무시(멱등).
     *
     * @param topic         Kafka 토픽(trading.order.filled / autoinvest.executed)
     * @param eventId       멱등키(결정적). 예 order:{id}:filled
     * @param aggregateType ORDER / AUTO_INVEST
     * @param aggregateId   추적용 id(orderId / executionId)
     * @param payload       이벤트 본문 객체(JSON 직렬화됨)
     */
    public void publish(String topic, String eventId, String aggregateType, Long aggregateId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이벤트 직렬화 실패: " + eventId);
        }
        try {
            outboxMapper.insert(Outbox.builder()
                    .eventId(eventId).topic(topic).aggregateType(aggregateType)
                    .aggregateId(aggregateId).payload(json).build());
        } catch (DuplicateKeyException e) {
            // 같은 사건 재실행(재시도·중복 호출) — 이미 기록됨. 알림 한 번만 나가면 되므로 무시.
            log.debug("[outbox] 중복 이벤트 무시 — {}", eventId);
        }
    }
}
