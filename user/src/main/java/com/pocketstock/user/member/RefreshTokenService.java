package com.pocketstock.user.member;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * refresh token을 Redis에 저장/조회/삭제.
 * key = "refresh:{token}", value = userId, TTL = 만료시간.
 * 로그아웃 시 삭제로 즉시 무효화할 수 있다(무상태 JWT 대비 장점).
 *
 * <p>userId로 토큰을 역조회할 수 있도록 "user-refresh:{userId}"(Set) 인덱스를 함께 유지한다.
 * 비밀번호 변경/재설정 시 해당 사용자의 모든 refresh token을 일괄 폐기하는 데 사용한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private static final String USER_KEY_PREFIX = "user-refresh:";

    private final StringRedisTemplate redis;

    @Value("${jwt.refresh-validity-ms:1209600000}") // 기본 14일
    private long refreshValidityMs;

    /** 새 refresh token 발급 후 Redis 저장(+ 사용자 인덱스 적재). */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMillis(refreshValidityMs);

        redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), ttl);

        String userKey = USER_KEY_PREFIX + userId;
        redis.opsForSet().add(userKey, token);
        redis.expire(userKey, ttl);   // 활성 사용자는 갱신, 비활성은 자동 만료(고아 키 방지)
        return token;
    }

    /** token으로 userId 조회(없거나 만료면 null) — /refresh 에서 사용. */
    public Long findUserId(String token) {
        String userId = redis.opsForValue().get(KEY_PREFIX + token);
        return userId == null ? null : Long.valueOf(userId);
    }

    /** token 폐기 — /logout 에서 사용. 사용자 인덱스에서도 제거. */
    public void revoke(String token) {
        String userId = redis.opsForValue().get(KEY_PREFIX + token);
        redis.delete(KEY_PREFIX + token);
        if (userId != null) {
            redis.opsForSet().remove(USER_KEY_PREFIX + userId, token);
        }
    }

    /** 해당 사용자의 모든 refresh token 폐기 — 비밀번호 변경/재설정 시 다른 세션 강제 만료. */
    public void revokeAllByUser(Long userId) {
        String userKey = USER_KEY_PREFIX + userId;
        Set<String> tokens = redis.opsForSet().members(userKey);
        if (tokens != null && !tokens.isEmpty()) {
            List<String> keys = tokens.stream().map(t -> KEY_PREFIX + t).toList();
            redis.delete(keys);
        }
        redis.delete(userKey);
    }
}
