package com.pocketstock.ledger.ls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LS 접근토큰 발급·캐싱 통합 테스트.
 * 실제 LS 호출 + Redis 필요 → 환경변수 LS_IT=true 일 때만 실행(평소 CI 스킵).
 * 자격증명은 local 프로파일(application-local.yml)에서 주입.
 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "LS_IT", matches = "true")
class LsTokenProviderIT {

    @Autowired
    private LsTokenProvider provider;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearCache() {
        redis.delete(LsTokenProvider.CACHE_KEY);
    }

    @Test
    @DisplayName("접근토큰을 발급하고 Redis에 TTL과 함께 캐싱하며, 재호출 시 캐시를 반환한다")
    void issueAndCache() {
        // LS가 토큰 형식(JWT 등)을 보장하지 않으므로 형식이 아닌 발급·캐시 동작만 검증
        String first = provider.getAccessToken();
        assertThat(first).isNotBlank();

        // 두 번째 호출은 캐시 적중 → 동일 토큰
        String second = provider.getAccessToken();
        assertThat(second).isEqualTo(first);

        // Redis에 양수 TTL로 저장됨(만료 여유 반영)
        Long ttl = redis.getExpire(LsTokenProvider.CACHE_KEY);
        assertThat(ttl).isNotNull().isGreaterThan(0);
    }
}
