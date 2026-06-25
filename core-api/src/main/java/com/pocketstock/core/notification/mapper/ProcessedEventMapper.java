package com.pocketstock.core.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Kafka consumer 멱등 처리이력(#204). at-least-once 중복 배달을 event_id PK로 흡수.
 */
@Mapper
public interface ProcessedEventMapper {

    /** 처리 마킹 시도 — 이미 있으면 0행(이미 처리한 이벤트 = 스킵). 1행이면 이번이 첫 처리. */
    int markProcessed(@Param("eventId") String eventId);
}
