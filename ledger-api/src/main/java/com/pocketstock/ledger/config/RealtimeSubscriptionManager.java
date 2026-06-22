package com.pocketstock.ledger.config;

import com.pocketstock.common.exception.BusinessException;
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
     * 매칭 엔진이 켠 해외 호가 구독의 "현재 세션 등록키" — stockCode → 지금 구독 중인 key.
     * 해외 tr_key는 세션(정규장 D / 주간 R)에 따라 바뀌므로 release 때 재계산하면 키가 달라져
     * decrement가 빗나간다(refCount 누수·upstream 미해제). 그래서 등록한 key를 그대로 보관해 해제하고,
     * 세션이 전환되면 {@link #acquireForeignQuote}가 옛 키를 해제하며 이 값을 새 키로 갱신한다.
     * (국내 UH1은 tr_key가 결정적이라 보관 불필요. 클라 구독은 sessionSubs가 담당.)
     */
    private final Map<String, RealtimeKey> foreignQuoteKeys = new ConcurrentHashMap<>();

    /**
     * 매칭 엔진 전용 — 종목 호가(UH1) 구독 ON. 클라 STOMP 구독과 같은 참조계수를 공유하므로
     * 누가 켜든 상류엔 1번만 등록되고, 클라·PENDING이 모두 빠질 때만 실제 해제된다.
     * (국내 LS UH1 — 해외 KIS는 {@link #acquireForeignQuote} 참조.)
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

    /**
     * 매칭 엔진·liveness 전용 — 해외 종목 호가(KIS HDFSASP0) 구독을 "현재 세션 기준"으로 보장.
     * 멱등이라 반복 호출해도 안전하다(옵션 B liveness 스케줄러가 주기적으로 다시 부른다, #127):
     * <ul>
     *   <li>tr_key 조립은 {@code resolveForeign}이 서버시각으로 자동 결정 — 장 마감(CLOSED)이면
     *       null이라 등록 스킵(개장 시 liveness가 재무장).</li>
     *   <li>이미 같은 세션 키로 구독 중이면 no-op(참조계수 중복 증가 없음).</li>
     *   <li>정규장↔주간 전환으로 tr_key가 바뀌면 옛 키를 해제하고 새 키로 재등록한다.</li>
     * </ul>
     * 클라 STOMP 구독과 같은 참조계수를 공유한다(국내 {@link #acquireAsking}과 동형).
     */
    public synchronized void acquireForeignQuote(String stockCode) {
        RealtimeKey desired = resolve(FOREIGN_QUOTE_PREFIX + stockCode);
        if (desired == null) {
            return;   // 장 마감(CLOSED)·미매핑 — 개장 시 liveness가 재무장
        }
        RealtimeKey current = foreignQuoteKeys.get(stockCode);
        if (current != null && current.id().equals(desired.id())) {
            return;   // 이미 같은 세션 키로 구독 중 — 멱등(반복 호출 안전)
        }
        // 새 키를 먼저 등록한다 — increment가 등록 실패로 throw하면 상태를 남기지 않아
        // 다음 liveness 호출이 같은 키로 재시도할 수 있다(실패 등록이 멱등 가드에 막혀 영구 정지되는 것 방지).
        increment(desired);
        foreignQuoteKeys.put(stockCode, desired);
        if (current != null) {
            decrement(current);   // 정규장↔주간 세션 전환 — 새 키 등록 성공 후 옛 키 해제(무중단)
        }
    }

    /** 매칭 엔진 전용 — 해외 종목 호가(HDFSASP0) 구독 해제(그 종목 마지막 PENDING 종료 시). */
    public synchronized void releaseForeignQuote(String stockCode) {
        RealtimeKey key = foreignQuoteKeys.remove(stockCode);   // 보관 중인 현재 세션 키로 정확히 해제
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
            try {
                key.upstream().register(key.trCode(), key.trKey());
            } catch (RuntimeException e) {
                // 상류 등록 실패 → 참조계수 롤백(다음 호출이 0→1로 등록을 재시도하도록).
                if (refCounts.merge(key.id(), -1, Integer::sum) <= 0) {
                    refCounts.remove(key.id());
                }
                throw e;
            }
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
        String trKey;
        try {
            trKey = KisTrKey.of(sessionResolver.current(), stock);
        } catch (BusinessException e) {
            // 매핑 불가 거래소(예: 국내 종목을 해외 토픽에 구독) → 스킵. 구독 흐름으로 예외 전파 안 함.
            log.warn("해외 실시간 구독: tr_key 조립 실패 {} - {}", stockCode, e.getMessage());
            return null;
        }
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
