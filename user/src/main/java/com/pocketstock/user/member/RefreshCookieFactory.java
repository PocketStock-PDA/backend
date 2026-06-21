package com.pocketstock.user.member;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh token을 담는 HttpOnly 쿠키 생성기.
 *
 * <p>refresh token은 바디로 노출하지 않고 이 쿠키로만 오간다(XSS 시 JS 탈취 방지).
 * 보안 옵션:
 * <ul>
 *   <li>HttpOnly      — JS(document.cookie) 접근 차단</li>
 *   <li>Secure        — HTTPS에서만 전송(운영 true, 로컬 http는 false)</li>
 *   <li>SameSite=Lax  — 크로스사이트 요청엔 미전송(CSRF 방어). 프론트는 same-origin 프록시라 first-party로 동작</li>
 *   <li>Path=/api/auth — 인증 엔드포인트에만 전송(일반 API 요청엔 안 실림)</li>
 *   <li>Domain 미설정  — 응답을 내려준 호스트(프록시 호스트)에 자동 바인딩</li>
 * </ul>
 *
 * <p>Max-Age는 {@link RefreshTokenService}의 Redis TTL과 동일한 {@code jwt.refresh-validity-ms}를
 * 읽어 자동으로 일치시킨다.
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refreshToken";
    private static final String PATH = "/api/auth";

    private final Duration maxAge;
    private final boolean secure;

    public RefreshCookieFactory(
            @Value("${jwt.refresh-validity-ms:1209600000}") long refreshValidityMs,
            @Value("${auth.refresh-cookie.secure:true}") boolean secure) {
        this.maxAge = Duration.ofMillis(refreshValidityMs);
        this.secure = secure;
    }

    /** 로그인 시 — refresh token을 담은 쿠키. */
    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(maxAge).build();
    }

    /** 로그아웃 시 — 즉시 만료(Max-Age=0)시켜 브라우저에서 제거. */
    public ResponseCookie expired() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH);
    }
}
