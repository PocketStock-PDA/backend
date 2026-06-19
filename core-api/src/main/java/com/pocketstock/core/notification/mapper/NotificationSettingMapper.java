package com.pocketstock.core.notification.mapper;

import com.pocketstock.core.notification.dto.NotificationSettingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationSettingMapper {

    NotificationSettingRow findByUserId(@Param("userId") Long userId);

    /** user_id UNIQUE 기준 upsert. 최초 호출 시 row 생성. */
    void upsertToken(
            @Param("userId") Long userId,
            @Param("token") String token,
            @Param("platform") String platform
    );

    void upsertSettings(
            @Param("userId") Long userId,
            @Param("notifyTrade") boolean notifyTrade,
            @Param("notifyUnfilled") boolean notifyUnfilled,
            @Param("notifyGoal") boolean notifyGoal,
            @Param("notifyMarketing") boolean notifyMarketing
    );
}
