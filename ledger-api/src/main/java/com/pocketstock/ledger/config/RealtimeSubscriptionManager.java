package com.pocketstock.ledger.config;

import com.pocketstock.ledger.kis.KisRealtimeClient;
import com.pocketstock.ledger.ls.LsRealtimeClient;
import com.pocketstock.ledger.realtime.RealtimeUpstream;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.support.KisTrKey;
import com.pocketstock.ledger.trading.support.MarketSessionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>매핑: {@code /topic/stock/trade/{code}} → LS US3, {@code /topic/asking/{code}} → LS UH1,
 * {@code /topic/foreign/quote/{code}} → KIS HDFSASP0, {@code /topic/foreign/transaction/{code}} → KIS HDFSCNT0.
 * 환율({@code /topic/currency/usd-krw}, CUR)은 온디맨드가 아니라 상시구독이라
 * 여기서 다루지 않는다 — {@code CurrencyRatePinner} 참조.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeSubscriptionManager {

    private static final String STOCK_TRADE_PREFIX = "/topic/stock/trade/";
    private static final String ASKING_PREFIX = "/topic/asking/";
    private static final String FOREIGN_QUOTE_PREFIX = "/topic/foreign/quote/";
    private static final String FOREIGN_TRADE_PREFIX = "/topic/foreign/transaction/";

    private final LsRealtimeClient lsClient;
    private final KisRealtimeClient kisClient;
    private final StockMapper stockMapper;
    private final MarketSessionResolver sessionResolver;

    /** "broker|trCode|trKey" → 구독자 수. */
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();
    /** sessionId → (subscriptionId → 등록키) — UNSUBSCRIBE·DISCONNECT 시 역추적용. */
    private final Map<String, Map<String, RealtimeKey>> sessionSubs = new ConcurrentHashMap<>();

    /**
     * 매칭 엔진 전용 — 종목 호가(UH1) 구독 ON. 클라 STOMP 구독과 같은 참조계수를 공유하므로
     * 누가 켜든 상류엔 1번만 등록되고, 클라·PENDING이 모두 빠질 때만 실제 해제된다.
     * (국내 한정 — 해외 RSYM 키 매핑은 후속 범위.)
     */
    public void acquireAsking(String stockCode) {
        RealtimeKey key = resolve(ASKING_PREFIX + stockCode);
        if (key != null) {
            increment(key);
        }
    }

    /** 매칭 엔진 전용 — 종목 호가(UH1) 구독 해제(그 종목 마지막 PENDING 종료 시). */
    public void releaseAsking(String stockCode) {
        RealtimeKey key = resolve(ASKING_PREFIX + stockCode);
        if (key != null) {
            decrement(key);
        }
    }

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
        if (destination.startsWith(STOCK_TRADE_PREFIX)) {
            String code = destination.substring(STOCK_TRADE_PREFIX.length()).trim();
            if (!code.isEmpty()) {
                // LS US3 tr_key = 거래소구분(U=통합) + 단축코드 → 7자리 + 공백 3 = 10자리.
                // 명세 그대로 "U005930   " 형태. 패딩 없이 보내면 등록 ack만 오고 시세가 안 흐른다.
                return new RealtimeKey(lsClient, "US3", String.format("%-10s", "U" + code));
            }
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
            return resolveForeign("HDFSASP0", code);
        }
        if (destination.startsWith(FOREIGN_TRADE_PREFIX)) {
            String code = destination.substring(FOREIGN_TRADE_PREFIX.length()).trim();
            return resolveForeign("HDFSCNT0", code);
        }
        // 환율(CUR)은 상시구독(CurrencyRatePinner)이라 온디맨드 매핑에서 제외.
        return null;
    }

    /**
     * 해외 구독 destination(순수 종목코드, 예 AAPL) → KIS 등록키.
     * 종목코드로 거래소 조회 → 현재 세션(정규장/주간) 판정 → KIS tr_key(DNASAAPL/RBAQAAPL) 조립.
     * 클라는 prefix(D/R)·주간 시장코드(BAQ 등)를 몰라도 되며, 서버가 서버시각으로 자동 결정한다.
     * 종목 미존재·CLOSED(미국 장 사이·주말)면 null → 등록 스킵.
     */
    private RealtimeKey resolveForeign(String trCode, String stockCode) {
        if (stockCode.isEmpty()) {
            return null;
        }
        TradableStock stock = stockMapper.findByCode(stockCode);
        if (stock == null) {
            log.warn("해외 실시간 구독: 미존재 종목 {}", stockCode);
            return null;
        }
        String trKey = KisTrKey.of(sessionResolver.current(), stock);
        if (trKey == null) {
            log.info("해외 실시간 구독 스킵(장 마감): {}", stockCode);
            return null;
        }
        return new RealtimeKey(kisClient, trCode, trKey);
    }

    private record RealtimeKey(RealtimeUpstream upstream, String trCode, String trKey) {
        String id() {
            return upstream.name() + "|" + trCode + "|" + trKey;
        }
    }
}
