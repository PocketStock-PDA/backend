package com.pocketstock.ledger.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 매수 체결 시 그 종목의 실적·배당 일정을 즉시 수집한다 — {@code trading.order.filled}(BUY·FILLED) 구독.
 * 주간/연간 배치나 부팅 동기화를 기다리지 않고, 새로 산 종목의 일정이 바로 캘린더에 뜨게 한다.
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
    private final LedgerActivation activation;
    private final ObjectMapper objectMapper;

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

        boolean buy = "BUY".equals(e.path("side").asText());
        boolean filled = "FILLED".equals(e.path("status").asText());
        String stockCode = e.path("stockCode").asText(null);
        if (!buy || !filled || stockCode == null || stockCode.isBlank()) {
            return;   // 매수·체결만 대상
        }

        Thread t = new Thread(() -> {
            try {
                log.info("[일정수집] 매수 체결 — {} 실적·배당 즉시 수집", stockCode);
                earningsBatchService.syncByStockCode(stockCode);
                dividendBatchService.syncByStockCode(stockCode);
            } catch (Exception ex) {
                log.error("[일정수집] {} 수집 실패 — {}", stockCode, ex.getMessage());
            }
        }, "order-filled-calendar-sync");
        t.setDaemon(true);
        t.start();
    }
}
