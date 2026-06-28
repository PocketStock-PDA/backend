package com.pocketstock.ledger.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import com.pocketstock.ledger.trading.service.HoldingReplicaSyncService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 체결 이벤트({@code trading.order.filled})를 구독해 증권 캘린더 상태를 맞춘다.
 * <ul>
 *   <li><b>매수(BUY·FILLED)</b>: 그 종목 실적·배당 일정을 즉시 수집 — 주간/연간 배치나 부팅을 기다리지 않고 바로 노출.</li>
 *   <li><b>매도(SELL·FILLED)</b>: 전량 매도면 {@code holdings_replica}에서 제거 — 안 가진 종목 일정이 캘린더에 남지 않도록.</li>
 * </ul>
 *
 * <p>참고: 이 토픽은 소수점 배치체결·온주 지정가체결만 발행한다(즉시 온주체결은 미발행). 즉시체결 종목은
 * 부팅/주간 배치가 보완한다. 외부 API(DART·KIS) 호출이 길어 데몬 스레드에서 비동기로 돌려 컨슈머를 막지 않는다.
 * upsert 멱등이라 at-least-once 재배달도 무해. 단일활성 게이트로 Blue-Green 비활성 색은 skip한다.
 *
 * <p><b>컨슈머 그룹은 색({@code DEPLOY_COLOR})별로 분리한다.</b> 공유 그룹이면 ack-mode=record에서 비활성
 * 색이 가져간 이벤트의 offset이 커밋돼 활성 색이 영영 못 받는다(유실). 색별 그룹이면 각 색이 독립적으로
 * 전 메시지를 받아 활성 색은 항상 수신한다. 비활성 색은 skip만 하고, 그 색이 나중에 활성이 되면 부팅
 * 동기화가 보유 종목 일정을 backfill한다. (로컬/단일 실행은 {@code DEPLOY_COLOR} 미설정 → 그룹 suffix "single")
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFilledCalendarConsumer {

    private final EarningsBatchService earningsBatchService;
    private final DividendBatchService dividendBatchService;
    private final HoldingReplicaSyncService holdingReplicaSyncService;
    private final LedgerActivation activation;
    private final ObjectMapper objectMapper;

    /**
     * 일정 수집 전용 bounded executor — 레코드마다 새 스레드를 만들지 않아 backlog·earliest 리플레이 시
     * 외부 API(KIS·DART) 폭증을 막는다. 고정 2스레드 + 큐 100, 가득 차면 CallerRunsPolicy로 컨슈머 스레드가
     * 직접 처리(백프레셔 — 폭주 대신 컨슈밍이 느려짐).
     */
    private final ThreadPoolExecutor syncExecutor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100),
            runnable -> {
                Thread t = new Thread(runnable, "order-filled-calendar-sync");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @KafkaListener(topics = "trading.order.filled", groupId = "ledger-calendar-sync-${DEPLOY_COLOR:single}")
    public void onOrderFilled(String message) {
        if (!activation.isActive()) {
            return;
        }
        JsonNode e;
        try {
            e = objectMapper.readTree(message);
        } catch (Exception ex) {
            log.error("[일정수집] 체결 이벤트 파싱 실패 — {}", message, ex);
            return;   // 독약 메시지 무한재시도 방지(파싱 불가는 ack)
        }

        boolean filled = "FILLED".equals(e.path("status").asText());
        String side = e.path("side").asText(null);
        String stockCode = e.path("stockCode").asText(null);
        if (!filled || side == null || stockCode == null || stockCode.isBlank()) {
            return;   // 체결만 대상
        }

        // bounded executor에서 짧은 재시도(5초×3) — core-api 일시 장애를 흡수한다. 그래도 실패하면
        // backfill(부팅·주간/연간 배치·replica 동기화)이 보완한다(Kafka 재처리 대신 backfill 내구성).
        if ("BUY".equals(side)) {
            syncExecutor.submit(() ->
                    BootSyncRetry.runWithRetry("[일정수집] " + stockCode, 3, 5_000L, () -> {
                        log.info("[일정수집] 매수 체결 — {} 실적·배당 즉시 수집", stockCode);
                        earningsBatchService.syncByStockCode(stockCode);
                        dividendBatchService.syncByStockCode(stockCode);
                    }));
        } else if ("SELL".equals(side)) {
            JsonNode uid = e.path("userId");
            if (uid.isMissingNode() || uid.isNull()) {
                return;
            }
            long userId = uid.asLong();
            syncExecutor.submit(() ->
                    BootSyncRetry.runWithRetry("[보유복제] " + userId + "/" + stockCode, 3, 5_000L,
                            () -> holdingReplicaSyncService.removeIfSoldOut(userId, stockCode)));
        }
    }

    @PreDestroy
    void shutdown() {
        syncExecutor.shutdown();
    }
}
