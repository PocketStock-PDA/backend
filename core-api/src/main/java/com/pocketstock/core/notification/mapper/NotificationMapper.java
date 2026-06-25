package com.pocketstock.core.notification.mapper;

import com.pocketstock.core.notification.dto.NotificationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    /** 알림 생성(알림함 기록). 타 도메인 이벤트 → create() 경유. refType/refId는 딥링크용(없으면 null). */
    void insert(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("body") String body,
            @Param("refType") String refType,
            @Param("refId") Long refId
    );

    List<NotificationRow> findByUser(
            @Param("userId") Long userId,
            @Param("read") Boolean read,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countByUser(
            @Param("userId") Long userId,
            @Param("read") Boolean read
    );

    long countUnread(@Param("userId") Long userId);

    /** 본인 소유 + 아직 안 읽은 알림만 읽음 처리. 영향 행 수 반환. */
    int markRead(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    /** 본인 소유 여부 (소유권 위반 404 판별용). */
    boolean existsByIdAndUser(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    int markAllRead(@Param("userId") Long userId);
}
