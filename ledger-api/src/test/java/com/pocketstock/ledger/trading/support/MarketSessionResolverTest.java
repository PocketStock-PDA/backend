package com.pocketstock.ledger.trading.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 판정 경계 검증. 모든 시각은 절대시각(UTC Instant)으로 고정해 서버 타임존과 무관하게 검증한다.
 * 기준일: 2026-06-22(월), 2026-06-20(토). 6월이라 미국 동부는 서머타임(EDT, UTC-4).
 */
class MarketSessionResolverTest {

    private MarketSession at(String isoUtc) {
        Clock fixed = Clock.fixed(Instant.parse(isoUtc), ZoneOffset.UTC);
        return new MarketSessionResolver(fixed).current();
    }

    @Test
    @DisplayName("미국 정규장: 월요일 ET 10:00(=UTC 14:00) → REGULAR")
    void regular() {
        assertThat(at("2026-06-22T14:00:00Z")).isEqualTo(MarketSession.REGULAR);
    }

    @Test
    @DisplayName("미국 주간거래: 월요일 KST 11:00(=UTC 02:00) → DAY")
    void day() {
        assertThat(at("2026-06-22T02:00:00Z")).isEqualTo(MarketSession.DAY);
    }

    @Test
    @DisplayName("세션 사이 빈 시간: 월요일 KST 18:00(=UTC 09:00) → CLOSED")
    void gapBetweenSessions() {
        assertThat(at("2026-06-22T09:00:00Z")).isEqualTo(MarketSession.CLOSED);
    }

    @Test
    @DisplayName("주간거래 개장 경계: KST 10:00 포함 / 09:59 제외")
    void dayBoundary() {
        assertThat(at("2026-06-22T01:00:00Z")).isEqualTo(MarketSession.DAY);      // KST 10:00
        assertThat(at("2026-06-22T00:59:00Z")).isEqualTo(MarketSession.CLOSED);   // KST 09:59
    }

    @Test
    @DisplayName("정규장 마감 경계: ET 16:00 제외(=CLOSED)")
    void regularCloseBoundary() {
        assertThat(at("2026-06-22T20:00:00Z")).isEqualTo(MarketSession.CLOSED);   // ET 16:00
        assertThat(at("2026-06-22T19:59:00Z")).isEqualTo(MarketSession.REGULAR);  // ET 15:59
    }

    @Test
    @DisplayName("주말: 토요일 ET 10:00·KST 11:00 모두 CLOSED")
    void weekend() {
        assertThat(at("2026-06-20T14:00:00Z")).isEqualTo(MarketSession.CLOSED);   // 토 ET 10:00
        assertThat(at("2026-06-20T02:00:00Z")).isEqualTo(MarketSession.CLOSED);   // 토 KST 11:00
    }
}
