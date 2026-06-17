package com.pocketstock.ledger.config;

import com.pocketstock.ledger.kis.KisRealtimeClient;
import com.pocketstock.ledger.ls.LsRealtimeClient;
import com.pocketstock.ledger.realtime.RealtimeUpstream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 클라이언트의 STOMP 구독을 실시간 상류(LS·KIS) 등록으로 잇는 온디맨드 브리지.
 * 같은 종목을 여러 클라가 봐도 상류엔 1번만 등록(참조계수), 마지막 구독자가 떠나면 해제 →
 * 상류 실시간 등록 종목 수 한도를 아낀다.
 *
 * <p>SUBSCRIBE/UNSUBSCRIBE/DISCONNECT(연결 끊김) 이벤트를 모두 처리해
 * 구독 누수(등록만 되고 해제 안 됨)를 막는다.
 *
 * <p>매핑: {@code /topic/asking/{code}} → LS UH1, {@code /topic/foreign/quote/{code}} → KIS HDFSASP0.
 * US3·GSC·CUR 등은 {@link #resolve}에 케이스를 추가하면 된다.
 */
@Component
@RequiredArgsConstructor
public class RealtimeSubscriptionManager {

    private static final String ASKING_PREFIX = "/topic/asking/";
    private static final String FOREIGN_QUOTE_PREFIX = "/topic/foreign/quote/";

    private final LsRealtimeClient lsClient;
    private final KisRealtimeClient kisClient;

    /** "broker|trCode|trKey" → 구독자 수. */
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();
    /** sessionId → (subscriptionId → 등록키) — UNSUBSCRIBE·DISCONNECT 시 역추적용. */
    private final Map<String, Map<String, RealtimeKey>> sessionSubs = new ConcurrentHashMap<>();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        RealtimeKey key = resolve(accessor.getDestination());
        if (key == null) {
            return;
        }
        sessionSubs.computeIfAbsent(accessor.getSessionId(), k -> new ConcurrentHashMap<>())
                .put(accessor.getSubscriptionId(), key);
        increment(key);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, RealtimeKey> subs = sessionSubs.get(accessor.getSessionId());
        if (subs == null) {
            return;
        }
        RealtimeKey key = subs.remove(accessor.getSubscriptionId());
        if (key != null) {
            decrement(key);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Map<String, RealtimeKey> subs = sessionSubs.remove(event.getSessionId());
        if (subs == null) {
            return;
        }
        subs.values().forEach(this::decrement);
    }

    private synchronized void increment(RealtimeKey key) {
        int count = refCounts.merge(key.id(), 1, Integer::sum);
        if (count == 1) {
            key.upstream().register(key.trCode(), key.trKey());
        }
    }

    private synchronized void decrement(RealtimeKey key) {
        Integer cur = refCounts.get(key.id());
        if (cur == null) {
            return;
        }
        if (cur <= 1) {
            refCounts.remove(key.id());
            key.upstream().unregister(key.trCode(), key.trKey());
        } else {
            refCounts.put(key.id(), cur - 1);
        }
    }

    /** 구독 destination → 상류 등록키. 매핑 없으면 null(시세 외 토픽). */
    private RealtimeKey resolve(String destination) {
        if (destination == null) {
            return null;
        }
        if (destination.startsWith(ASKING_PREFIX)) {
            String code = destination.substring(ASKING_PREFIX.length()).trim();
            if (!code.isEmpty()) {
                // LS UH1 tr_key = 거래소구분(U=통합) + 단축코드, 7자리 + 공백 3 = 10자리.
                // prefix 없이 코드만 보내면 등록 ack만 오고 시세가 안 흐른다.
                return new RealtimeKey(lsClient, "UH1", String.format("%-10s", "U" + code));
            }
        }
        if (destination.startsWith(FOREIGN_QUOTE_PREFIX)) {
            String code = destination.substring(FOREIGN_QUOTE_PREFIX.length()).trim();
            if (!code.isEmpty()) {
                // KIS tr_key = R/D + 시장(3) + 종목 (예: RBAQAAPL). 클라가 완성형 코드로 구독.
                return new RealtimeKey(kisClient, "HDFSASP0", code);
            }
        }
        return null;
    }

    private record RealtimeKey(RealtimeUpstream upstream, String trCode, String trKey) {
        String id() {
            return upstream.name() + "|" + trCode + "|" + trKey;
        }
    }
}
