package com.pocketstock.ledger.ls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LS증권 접근토큰 공급자 — 시세·실시간·환전이 공유하는 공통 인프라.
 * Redis에 캐싱하고 만료 여유(REFRESH_MARGIN)를 두고 자동 재발급한다.
 * ※ TTL은 응답 expires_in(당일 만료까지 남은 초) 기반 — 절대 고정값 쓰지 않음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LsTokenProvider {

    static final String CACHE_KEY = "ls:access_token";
    /** 만료 직전 호출로 인한 인증 실패를 막는 여유 시간(초) */
    private static final long REFRESH_MARGIN_SEC = 600;

    private final LsTokenClient tokenClient;
    private final StringRedisTemplate redis;

    /** 유효한 접근토큰 반환(캐시 우선, 없으면 발급 후 캐싱). */
    public String getAccessToken() {
        String cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        return issueAndCache();
    }

    /** 캐시 강제 무효화 후 재발급(토큰 거부 응답 시 호출). */
    public String refresh() {
        redis.delete(CACHE_KEY);
        return issueAndCache();
    }

    private synchronized String issueAndCache() {
        // 락 획득 사이 다른 스레드가 채웠을 수 있으니 재확인(인스턴스 내 stampede 방지)
        String cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        LsTokenResponse res = tokenClient.issueToken();
        long ttl = Math.max(res.expiresIn() - REFRESH_MARGIN_SEC, 1);
        redis.opsForValue().set(CACHE_KEY, res.accessToken(), Duration.ofSeconds(ttl));
        log.info("LS 접근토큰 캐시 갱신 (ttl={}s)", ttl);
        return res.accessToken();
    }
}
