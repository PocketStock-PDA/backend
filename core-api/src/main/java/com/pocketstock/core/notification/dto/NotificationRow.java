package com.pocketstock.core.notification.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * notifications 테이블 조회 결과 매핑용 row.
 * boolean 필드는 'is' 접두사를 빼야 Lombok setter(setRead)와 MyBatis 프로퍼티명(read)이 일치한다.
 */
@Data
public class NotificationRow {
    private Long id;
    private String type;
    private String title;
    private String body;
    private boolean read;
    private LocalDateTime createdAt;
}
