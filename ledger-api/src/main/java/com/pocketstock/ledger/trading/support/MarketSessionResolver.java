package com.pocketstock.ledger.trading.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 현재 미국 시장 세션을 판정한다(정규장/주간거래/마감).
 *
 * <p>절대시각({@link Instant})에서 출발해 정규장은 미국 동부시간, 주간거래는 한국시간으로
 * 각각 변환해 판정하므로 서버 타임존(UTC 고정)과 무관하게 결과가 동일하다.
 * 서머타임(DST)은 {@code America/New_York} 존이 자동 처리한다.
 *
 * <p>정규장(ET 09:30~16:00)과 주간거래(KST 10:00~16:00)는 시간대가 겹치지 않아 우선순위 고민이 없다.
 * 주말은 변환된 존 기준 요일로 거른다(정규=ET 요일·주간=KST 요일).
 *
 * <p><b>휴장일 캘린더는 만들지 않는다(확정 결정).</b> 미국 공휴일이어도 평일이면 세션으로 잡혀
 * 라이브를 시도하지만, KIS가 빈 응답을 주므로 read-through가 마지막 스냅샷(동결가)으로 흡수한다
 * (#145 통합 체결 정책). 즉 캘린더 없이도 시스템은 안전 — 시계 기반 판정만 유지한다.
 */
@Component
public class MarketSessionResolver {

    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // ET 09:30~16:00 = KST 여름 22:30~05:00 / 겨울 23:30~06:00 (DST는 ET 존이 자동 처리)
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime DAY_OPEN = LocalTime.of(10, 0);
    private static final LocalTime DAY_CLOSE = LocalTime.of(16, 0);

    private final Clock clock;

    public MarketSessionResolver() {
        this(Clock.systemUTC());
    }

    /** 테스트에서 고정 시각 주입용. */
    MarketSessionResolver(Clock clock) {
        this.clock = clock;
    }

    /** 현재 시각 기준 미국 시장 세션. */
    public MarketSession current() {
        Instant now = clock.instant();
        if (isUsRegular(now)) {
            return MarketSession.REGULAR;
        }
        if (isUsDay(now)) {
            return MarketSession.DAY;
        }
        return MarketSession.CLOSED;
    }

    private boolean isUsRegular(Instant now) {
        ZonedDateTime et = now.atZone(ET);
        return isWeekday(et) && withinSession(et, REGULAR_OPEN, REGULAR_CLOSE);
    }

    private boolean isUsDay(Instant now) {
        ZonedDateTime kst = now.atZone(KST);
        return isWeekday(kst) && withinSession(kst, DAY_OPEN, DAY_CLOSE);
    }

    private static boolean isWeekday(ZonedDateTime zdt) {
        DayOfWeek d = zdt.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    /** [open, close) — 개장 포함, 마감 제외. */
    private static boolean withinSession(ZonedDateTime zdt, LocalTime open, LocalTime close) {
        LocalTime t = zdt.toLocalTime();
        return !t.isBefore(open) && t.isBefore(close);
    }
}
