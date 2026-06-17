package com.pocketstock.user.member;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * refresh token을 Redis에 저장/조회/삭제.
 * key = "refresh:{token}", value = userId, TTL = 만료시간.
 * 로그아웃 시 삭제로 즉시 무효화할 수 있다(무상태 JWT 대비 장점).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;

    @Value("${jwt.refresh-validity-ms:1209600000}") // 기본 14일
    private long refreshValidityMs;

    /** 새 refresh token 발급 후 Redis 저장. */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId),
                Duration.ofMillis(refreshValidityMs));
        return token;
    }

    /** token으로 userId 조회(없거나 만료면 null) — /refresh 에서 사용. */
    public Long findUserId(String token) {
        String userId = redis.opsForValue().get(KEY_PREFIX + token);
        return userId == null ? null : Long.valueOf(userId);
    }

    /** token 폐기 — /logout 에서 사용. */
    public void revoke(String token) {
        redis.delete(KEY_PREFIX + token);
    }
}
