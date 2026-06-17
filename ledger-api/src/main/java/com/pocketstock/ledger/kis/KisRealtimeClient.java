package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.ledger.realtime.RealtimeUpstream;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
 * 한국투자증권(KIS) 해외주식 실시간 WebSocket 상류 클라이언트 — 인스턴스당 영속 1세션.
 * 구독자가 생기면 {@link #register}, 0이 되면 {@link #unregister}로 종목 실시간을 켜고/끈다.
 *
 * <p>KIS 프로토콜은 LS와 다르다:
 * <ul>
 *   <li>구독 프레임은 JSON: header(approval_key/custtype/tr_type) + body.input(tr_id/tr_key)</li>
 *   <li>인증은 approval_key({@link KisApprovalKeyProvider}) — 첫 프레임 헤더에 넣음</li>
 *   <li>데이터 프레임은 JSON이 아니라 {@code 0|HDFSASP0|001|f1^f2^...} 파이프+캐럿 구분</li>
 *   <li>주기적 PINGPONG 프레임은 그대로 echo</li>
 * </ul>
 *
 * <p>주의: 현재는 단일 인스턴스 기준(멀티 인스턴스 리더 세션은 후속, backend#24).
 */
@Slf4j
@Component
public class KisRealtimeClient implements RealtimeUpstream {

    private static final String TR_TYPE_REGISTER = "1";   // 등록
    private static final String TR_TYPE_UNREGISTER = "2"; // 해제
    private static final String CUST_TYPE_INDIVIDUAL = "P";
    private static final String PINGPONG = "PINGPONG";
    private static final String ENCRYPTED_FLAG = "1";

    private final KisApiProperties props;
    private final KisApprovalKeyProvider approvalKeyProvider;
    private final ObjectMapper objectMapper;

    /** tr_id → 도메인 핸들러. */
    private final Map<String, KisRealtimeListener> listeners;
    /** 현재 등록 중인 "tr_id|tr_key" — 재연결 시 복구용. */
    private final Set<String> activeKeys = ConcurrentHashMap.newKeySet();

    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();
    private volatile WebSocketSession session;
    private final Object connectLock = new Object();

    public KisRealtimeClient(KisApiProperties props, KisApprovalKeyProvider approvalKeyProvider,
                             ObjectMapper objectMapper, List<KisRealtimeListener> listenerBeans) {
        this.props = props;
        this.approvalKeyProvider = approvalKeyProvider;
        this.objectMapper = objectMapper;
        this.listeners = listenerBeans.stream()
                .collect(Collectors.toMap(KisRealtimeListener::trId, l -> l));
    }

    @Override
    public String name() {
        return "KIS";
    }

    @Override
    public synchronized void register(String trId, String trKey) {
        connectIfNeeded();
        if (activeKeys.add(key(trId, trKey))) {
            send(TR_TYPE_REGISTER, trId, trKey);
            log.info("KIS 실시간 등록 tr_id={} tr_key={}", trId, trKey);
        }
    }

    @Override
    public synchronized void unregister(String trId, String trKey) {
        if (activeKeys.remove(key(trId, trKey)) && isOpen()) {
            send(TR_TYPE_UNREGISTER, trId, trKey);
            log.info("KIS 실시간 해제 tr_id={} tr_key={}", trId, trKey);
        }
    }

    private void connectIfNeeded() {
        if (isOpen()) {
            return;
        }
        synchronized (connectLock) {
            if (isOpen()) {
                return;
            }
            String url = props.getRealtimeUrl();
            try {
                session = wsClient.execute(new InboundHandler(), url).get();
                log.info("KIS 실시간 WebSocket 연결: {}", url);
                activeKeys.forEach(k -> {
                    String[] p = k.split("\\|", 2);
                    send(TR_TYPE_REGISTER, p[0], p[1]);
                });
            } catch (Exception e) {
                session = null;
                Thread.currentThread().interrupt();
                throw new IllegalStateException("KIS 실시간 WebSocket 연결 실패: " + url, e);
            }
        }
    }

    /** 등록/해제 프레임 — header(approval_key/custtype/tr_type) + body.input(tr_id/tr_key). */
    private void send(String trType, String trId, String trKey) {
        try {
            Map<String, Object> frame = Map.of(
                    "header", Map.of(
                            "approval_key", approvalKeyProvider.getApprovalKey(),
                            "custtype", CUST_TYPE_INDIVIDUAL,
                            "tr_type", trType,
                            "content-type", "utf-8"),
                    "body", Map.of("input", Map.of("tr_id", trId, "tr_key", trKey)));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
        } catch (Exception e) {
            throw new IllegalStateException("KIS 실시간 프레임 전송 실패 tr_id=" + trId, e);
        }
    }

    /**
     * 인바운드 라우팅. JSON(구독 ack/PINGPONG)과 데이터 프레임을 구분 처리한다.
     * 데이터 프레임: {@code 암호화구분|tr_id|건수|f1^f2^...}
     */
    private void dispatch(String raw) {
        try {
            if (raw.startsWith("{")) {
                handleJsonFrame(raw);
                return;
            }
            String[] parts = raw.split("\\|", 4);
            if (parts.length < 4) {
                return;
            }
            if (ENCRYPTED_FLAG.equals(parts[0])) {
                log.warn("KIS 암호화 프레임 수신 — 미지원 tr_id={}", parts[1]);
                return;
            }
            String trId = parts[1];
            KisRealtimeListener listener = listeners.get(trId);
            if (listener != null) {
                listener.onData(parts[3].split("\\^"));
            }
        } catch (Exception e) {
            log.warn("KIS 실시간 메시지 처리 실패: {}", e.getMessage());
        }
    }

    /** 구독 응답(ack) 또는 PINGPONG. PINGPONG은 그대로 echo 해 세션을 유지한다. */
    private void handleJsonFrame(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        String trId = root.path("header").path("tr_id").asText("");
        if (PINGPONG.equals(trId)) {
            WebSocketSession s = session;
            if (s != null && s.isOpen()) {
                s.sendMessage(new TextMessage(raw));
            }
            return;
        }
        String msg = root.path("body").path("msg1").asText("");
        log.debug("KIS 구독 응답 tr_id={} msg={}", trId, msg);
    }

    private boolean isOpen() {
        WebSocketSession s = session;
        return s != null && s.isOpen();
    }

    private String key(String trId, String trKey) {
        return trId + "|" + trKey;
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
            log.warn("KIS 실시간 WebSocket 종료: {} — 다음 등록 시 재연결·재등록", status);
            session = null;
        }

        @Override
        public void handleTransportError(WebSocketSession s, Throwable ex) {
            log.error("KIS 실시간 WebSocket 오류", ex);
        }
    }
}
