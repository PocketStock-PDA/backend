package com.pocketstock.user.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 매 요청에서 Authorization 헤더의 JWT를 검증해 user_id를 request attribute에 심는다.
 * spring-security 미사용 - 순수 Servlet Filter(OncePerRequestFilter는 spring-web 소속).
 * DB 안 봄 - ledger도 그대로 사용(DB A 결합 없음).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTR = "userId";

    private final JwtProvider jwtProvider;

    public JwtAuthFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Long userId = jwtProvider.parseUserId(header.substring(7));
                req.setAttribute(USER_ID_ATTR, userId);
            } catch (Exception ignored) {
                // 무효/만료 토큰 - attribute 미설정(인증 안 된 요청으로 처리)
            }
        }
        chain.doFilter(req, res);
    }
}
