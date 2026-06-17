package com.pocketstock.ledger.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간시세 STOMP 설정 — 도메인 무관 공통이라 ledger 루트 config에 둔다.
 * 클라이언트는 {@code /ws}로 연결(네이티브 WebSocket, SockJS 미사용),
 * {@code /topic/...} 구독으로 서버 push를 받는다.
 *
 * <p>현재는 SimpleBroker(인메모리) 단독 — 단일 인스턴스 기준.
 * 멀티 인스턴스 팬아웃(Redis Pub/Sub 백플레인)·LS 리더 세션은 후속 단계(backend#24).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompJwtInterceptor stompJwtInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 모바일 네이티브 클라이언트 → SockJS 폴백 불필요. CORS는 dev 편의상 전체 허용.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 모든 토픽이 서버→클라 단방향 push → SimpleBroker(/topic)만. 클라→서버 SEND 없음.
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtInterceptor);
    }
}
