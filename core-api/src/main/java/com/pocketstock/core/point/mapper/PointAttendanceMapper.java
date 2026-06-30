package com.pocketstock.core.point.mapper;

import com.pocketstock.core.point.domain.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface PointAttendanceMapper {

    /** 가장 최근 출석 1건(없으면 null). checkedToday/streak 산정 기준. */
    AttendanceRecord findLastAttendance(@Param("userId") Long userId);

    /** 출석 이력 append. UNIQUE(user_id, attended_date) 위반 시 DuplicateKeyException. */
    int insertAttendance(@Param("userId") Long userId,
                         @Param("attendedDate") LocalDate attendedDate,
                         @Param("awarded") int awarded,
                         @Param("streakAfter") int streakAfter);

    /** 마이신한포인트(SHINHAN_POINT, LINKED) 잔액 증액. 대상 행이 없으면 0건. */
    int increaseShinhanPoint(@Param("userId") Long userId,
                             @Param("amount") int amount);

    /** 마이신한포인트(SHINHAN_POINT, LINKED) 현재 잔액(없으면 null). */
    Long findShinhanPointBalance(@Param("userId") Long userId);
}
