package com.pocketstock.user.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급·검증 도구 (무상태 — DB 안 봄).
 * user 모듈 소속이지만 core·ledger 둘 다 공유.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long validityMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.validity-ms:3600000}") long validityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMs = validityMs;
    }

    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }

    /** 액세스 토큰 유효시간(초) — 로그인 응답의 expiresIn 용. */
    public long getValiditySeconds() {
        return validityMs / 1000;
    }
}
