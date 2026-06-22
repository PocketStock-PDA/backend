package com.pocketstock.ledger.ls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.ledger.realtime.RealtimeReconnectedEvent;
import com.pocketstock.ledger.realtime.RealtimeUpstream;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LS 실시간 시세 WebSocket 상류 클라이언트 — 인스턴스당 영속 1세션을 유지한다.
 * 토픽 구독자가 생기면 {@link #register}, 0이 되면 {@link #unregister}로
 * LS에 종목별 실시간 시세를 켜고/끈다(온디맨드 — LS 등록 종목 수 한도 회피).
 *
 * <p>인바운드 프레임은 header.tr_cd 로 {@link LsRealtimeListener}에 라우팅한다.
 * 토큰은 {@link LsTokenProvider}를 재사용한다.
 *
 * <p>주의: 현재는 단일 인스턴스 기준. 멀티 인스턴스에선 인스턴스마다 LS 세션을
 * 열어 LS 한도를 N배 소모하므로, 리더 1대만 연결하는 방식이 후속(backend#24).
 */
@Slf4j
@Component
public class LsRealtimeClient implements RealtimeUpstream {

    private static final String TR_TYPE_REGISTER = "3";   // 실시간 시세 등록
    private static final String TR_TYPE_UNREGISTER = "4"; // 실시간 시세 해제

    private final LsApiProperties props;
    private final LsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** tr_cd → 도메인 핸들러. */
    private final Map<String, LsRealtimeListener> listeners;
    /** 현재 등록 중인 "tr_cd|tr_key" — 재연결 시 복구용. */
    private final Set<String> activeKeys = ConcurrentHashMap.newKeySet();

    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();
    private volatile WebSocketSession session;
    private final Object connectLock = new Object();
    /** 첫 연결 이후 true — 첫 연결과 '재연결(down→up)'을 구분해 재연결에만 보정 이벤트를 쏜다. */
    private volatile boolean everConnected = false;

    public LsRealtimeClient(LsApiProperties props, LsTokenProvider tokenProvider,
                            ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher,
                            List<LsRealtimeListener> listenerBeans) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.listeners = listenerBeans.stream()
                .collect(Collectors.toMap(LsRealtimeListener::trCd, l -> l));
    }

    @Override
    public String name() {
        return "LS";
    }

    /** 종목 실시간 등록(구독자 0→1일 때만 호출됨). */
    @Override
    public synchronized void register(String trCd, String trKey) {
        connectIfNeeded();
        if (activeKeys.add(key(trCd, trKey))) {
            send(TR_TYPE_REGISTER, trCd, trKey);
            log.info("LS 실시간 등록 tr_cd={} tr_key={}", trCd, trKey);
        }
    }

    /** 종목 실시간 해제(구독자 1→0일 때만 호출됨). */
    @Override
    public synchronized void unregister(String trCd, String trKey) {
        if (activeKeys.remove(key(trCd, trKey)) && isOpen()) {
            send(TR_TYPE_UNREGISTER, trCd, trKey);
            log.info("LS 실시간 해제 tr_cd={} tr_key={}", trCd, trKey);
        }
    }

    /**
     * 세션 유지(옵션 B 지원, #127) — 등록 종목이 있는데 세션이 끊겼으면 재연결한다
     * ({@code connectIfNeeded}가 activeKeys 재등록 + 재연결 이벤트 발행까지 수행).
     * 평소엔 환율(CUR) 하트비트가 같은 LS 세션을 살려 사실상 no-op이지만, 매칭 엔진이 환율 핀에
     * 의존하지 않고 PENDING 세션을 직접 챙기도록(국내·해외 동형) 호출 경로를 제공한다.
     */
    public synchronized void reconnectIfStale() {
        if (!activeKeys.isEmpty() && !isOpen()) {
            connectIfNeeded();
        }
    }

    private void connectIfNeeded() {
        if (isOpen()) {
            return;
        }
        boolean reconnected = false;
        synchronized (connectLock) {
            if (!isOpen()) {
                String url = props.getRealtimeUrl();
                try {
                    session = wsClient.execute(new InboundHandler(), url).get();
                    log.info("LS 실시간 WebSocket 연결: {}", url);
                    // 재연결이면 끊기기 전 등록 종목을 다시 켠다.
                    activeKeys.forEach(k -> {
                        String[] p = k.split("\\|", 2);
                        send(TR_TYPE_REGISTER, p[0], p[1]);
                    });
                    reconnected = everConnected;   // 직전에 한 번이라도 붙었었다면 이번은 '재연결'
                    everConnected = true;
                } catch (Exception e) {
                    session = null;
                    // 인터럽트일 때만 플래그 복원 — 그 외 예외(연결·실행 오류)엔 호출 스레드 오염 금지.
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new IllegalStateException("LS 실시간 WebSocket 연결 실패: " + url, e);
                }
            }
        }
        // 잠금 밖에서 발행 — 소비자(매칭 엔진)의 REST 스냅샷 보정이 connectLock을 물지 않게.
        if (reconnected) {
            eventPublisher.publishEvent(new RealtimeReconnectedEvent(name()));
        }
    }

    /** 등록/해제 프레임 전송. header.token + tr_type, body.tr_cd + tr_key. */
    private void send(String trType, String trCd, String trKey) {
        try {
            Map<String, Object> frame = Map.of(
                    "header", Map.of("token", tokenProvider.getAccessToken(), "tr_type", trType),
                    "body", Map.of("tr_cd", trCd, "tr_key", trKey));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
        } catch (Exception e) {
            throw new IllegalStateException("LS 실시간 프레임 전송 실패 tr_cd=" + trCd, e);
        }
    }

    /** 인바운드 프레임 라우팅 — 등록 ack(빈 body)는 건너뛰고 데이터만 핸들러로. */
    private void dispatch(String payload) {
        log.debug("LS 실시간 수신 raw: {}", payload); // 진단용 — 안정화 후 제거
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode header = root.path("header");
            String trCd = header.path("tr_cd").asText(null);
            if (trCd == null) {
                return;
            }
            JsonNode body = root.path("body");
            if (body.isMissingNode() || body.isNull() || body.isEmpty()) {
                log.debug("LS 실시간 ack/빈 프레임 tr_cd={} rsp={}", trCd, header.path("rsp_msg").asText(""));
                return;
            }
            LsRealtimeListener listener = listeners.get(trCd);
            log.debug("LS 실시간 데이터 프레임 tr_cd={} → {}", trCd, listener != null ? "라우팅" : "리스너 없음(드롭)");
            if (listener != null) {
                listener.onData(body);
            }
        } catch (Exception e) {
            log.warn("LS 실시간 메시지 처리 실패: {}", e.getMessage());
        }
    }

    private boolean isOpen() {
        WebSocketSession s = session;
        return s != null && s.isOpen();
    }

    private String key(String trCd, String trKey) {
        return trCd + "|" + trKey;
    }

    @PreDestroy
    void close() {
        WebSocketSession s = session;
        if (s != null && s.isOpen()) {
            try {
                s.close();
            } catch (Exception ignored) {
                // 종료 중 — 무시
            }
        }
    }

    private class InboundHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) {
            dispatch(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            log.warn("LS 실시간 WebSocket 종료: {} — 다음 등록 시 재연결·재등록", status);
            session = null;
        }

        @Override
        public void handleTransportError(WebSocketSession s, Throwable ex) {
            log.error("LS 실시간 WebSocket 오류", ex);
        }
    }
}
