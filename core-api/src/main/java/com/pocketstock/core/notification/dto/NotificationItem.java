package com.pocketstock.core.notification.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationItem(
        Long id,
        String type,
        String title,
        String body,
        String tag,
        String url,
        String occurredAt,
        Map<String, Object> data,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationItem from(NotificationRow row, ObjectMapper objectMapper) {
        return new NotificationItem(
                row.getId(),
                row.getType(),
                row.getTitle(),
                row.getBody(),
                row.getTag(),
                row.getUrl(),
                row.getOccurredAt(),
                parseData(row.getDataJson(), objectMapper),
                row.isRead(),
                row.getCreatedAt()
        );
    }

    private static Map<String, Object> parseData(String dataJson, ObjectMapper objectMapper) {
        if (dataJson == null || dataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.reader()
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .forType(new TypeReference<Map<String, Object>>() {})
                    .readValue(dataJson);
        } catch (Exception e) {
            return null;
        }
    }
}
