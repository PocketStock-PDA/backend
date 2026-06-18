package com.pocketstock.ledger.config;

import com.pocketstock.user.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP CONNECT 프레임의 JWT를 검증해 세션 Principal(userId)로 심는다.
 * REST의 {@code JwtAuthFilter}와 동일 정책(무상태·DB 안 봄)으로 {@link JwtProvider} 공유.
 * 연결 시 1회만 검증하면 같은 WebSocket 세션의 이후 SUBSCRIBE에 Principal이 유지된다.
 *
 * <p>정책: 토큰 없으면 익명 허용(공개 시세 토픽 구독 가능),
 * 토큰이 있는데 무효/만료면 연결 거부. 체결통보(convertAndSendToUser)는 Principal 필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompJwtInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = bearerToken(accessor.getFirstNativeHeader("Authorization"));
        if (token == null) {
            log.debug("STOMP 익명 연결(Authorization 없음) — 공개 시세 토픽만 구독 가능");
            return message;
        }

        Long userId = jwtProvider.parseUserId(token); // 무효/만료면 예외 → CONNECT 거부
        accessor.setUser(new StompPrincipal(userId));
        log.debug("STOMP 연결 인증 성공 userId={}", userId);
        return message;
    }

    private String bearerToken(String header) {
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /** convertAndSendToUser 의 user 식별자로 쓰는 세션 Principal. name=userId. */
    private record StompPrincipal(Long userId) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(userId);
        }
    }
}
