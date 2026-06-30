package com.pocketstock.core.point;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.point.domain.AttendanceRecord;
import com.pocketstock.core.point.dto.AttendanceCheckResponse;
import com.pocketstock.core.point.dto.AttendanceStatusResponse;
import com.pocketstock.core.point.mapper.PointAttendanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 포인트 출석체크 — 하루 1회 마이신한포인트(SHINHAN_POINT) 적립.
 *
 * <p>적립 대상 잔액은 linked_points(DB A, 연동 자산 사본·목데이터 영역, 1P=1원 BIGINT)다.
 * 원장(ledger-api)이 아니므로 append-only가 아닌 단순 증액 UPDATE로 처리한다.
 * 적립분은 collectSources(POINT/신한 항목)가 같은 테이블을 읽으므로 별도 반영 작업 없이 노출된다.</p>
 *
 * <p>일자 기준은 서버 KST(Asia/Seoul). JVM 기본 TZ가 UTC이므로 명시 변환한다(자정 경계 버그 방지).</p>
 */
@Service
@RequiredArgsConstructor
public class PointAttendanceService {

    /** 1회 출석 적립 포인트(현재 정책 고정). 후속 이벤트 가변화 시 이 상수만 외부화한다. */
    private static final int DAILY_REWARD = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PointAttendanceMapper mapper;

    @Transactional(readOnly = true)
    public AttendanceStatusResponse getStatus(Long userId) {
        LocalDate today = LocalDate.now(KST);
        AttendanceRecord last = mapper.findLastAttendance(userId);

        boolean checkedToday = last != null && last.attendedDate().isEqual(today);
        int streak = last != null ? last.streakAfter() : 0;
        LocalDate lastCheckedDate = last != null ? last.attendedDate() : null;

        return new AttendanceStatusResponse(checkedToday, streak, lastCheckedDate, DAILY_REWARD);
    }

    /**
     * 출석체크 적립. 멱등: 이미 오늘 출석했으면 추가 적립 없이 awarded:0 + 현재 streak/balance 반환.
     * 출석 이력 append와 포인트 증액을 단일 DB A 트랜잭션으로 묶어 부분성공을 막는다.
     */
    @Transactional
    public AttendanceCheckResponse check(Long userId) {
        LocalDate today = LocalDate.now(KST);
        AttendanceRecord last = mapper.findLastAttendance(userId);

        // 이미 오늘 출석 → 멱등 처리(추가 적립 없음)
        if (last != null && last.attendedDate().isEqual(today)) {
            return new AttendanceCheckResponse(0, last.streakAfter(), currentBalance(userId));
        }

        // 연속 일수: 어제 출석했으면 +1, 아니면(거름/첫 출석) 1로 리셋
        int newStreak = (last != null && last.attendedDate().isEqual(today.minusDays(1)))
                ? last.streakAfter() + 1
                : 1;

        // 이력 append — 동시요청 레이스는 UNIQUE(user_id, attended_date)가 막는다.
        try {
            mapper.insertAttendance(userId, today, DAILY_REWARD, newStreak);
        } catch (DuplicateKeyException e) {
            // 같은 날 동시 호출로 먼저 한 건이 들어간 경우 → 멱등 처리
            return new AttendanceCheckResponse(0, newStreak, currentBalance(userId));
        }

        // 마이신한포인트 증액. 대상 행(LINKED SHINHAN_POINT)이 없으면 0건 → 롤백(적립 실패=5xx)
        int updated = mapper.increaseShinhanPoint(userId, DAILY_REWARD);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "마이신한포인트 적립 대상을 찾지 못했습니다. (userId=" + userId + ")");
        }

        return new AttendanceCheckResponse(DAILY_REWARD, newStreak, currentBalance(userId));
    }

    private long currentBalance(Long userId) {
        Long balance = mapper.findShinhanPointBalance(userId);
        return balance != null ? balance : 0L;
    }
}
