package com.pocketstock.ledger.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 보유 스냅샷 1회 동기화 — 10분 주기({@link HoldingReplicaSyncService#scheduledSync()})를 기다리지
 * 않고 앱이 뜰 때마다 {@code holdings_replica}를 즉시 맞춘다. 매수 후 재기동하면 캘린더에 일정이 바로 보인다.
 *
 * <p>{@code ApplicationReadyEvent} 리스너는 동기라 부팅 완료를 잡아두지 않도록 데몬 스레드에서 돌린다.
 * core-api가 아직 안 떴으면 그 회차는 실패해 로그만 남고(크래시 아님), 주기 동기화가 백업한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingReplicaBootSync {

    private final HoldingReplicaSyncService syncService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread t = new Thread(() -> {
            try {
                log.info("[보유복제] 부팅 동기화 시작");
                syncService.sync();
            } catch (Exception e) {
                log.error("[보유복제] 부팅 동기화 실패 — {}", e.getMessage(), e);
            }
        }, "holding-replica-boot-sync");
        t.setDaemon(true);
        t.start();
    }
}
