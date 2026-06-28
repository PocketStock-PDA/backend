package com.pocketstock.ledger.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 배당 일정 1회 동기화 — 연 1회 크론({@link DividendBatchService#syncDividendEvents()}, 1월 1일 02시)을
 * 기다리지 않고 앱이 뜰 때마다 향후 1년치 배당기준일·배당락일·지급일을 즉시 채운다.
 *
 * <p>배치 자체가 단일활성 게이트({@code LedgerActivation.isActive()})로 보호되어, Blue-Green에선 활성 색만
 * 실행하고 비활성 색은 skip한다(중복 upsert 없음).
 *
 * <p>{@code ApplicationReadyEvent} 리스너는 동기라, 전체 종목 1년치 배당 조회가 부팅 완료를 잡아두지
 * 않도록 데몬 스레드에서 비동기로 돌린다. core-api가 아직 안 떴으면 그 회차 upsert는 실패해 로그만 남고
 * (크래시 아님), 다음 부팅이나 연간 크론이 백업한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendBootSync {

    private final DividendBatchService dividendBatchService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread t = new Thread(() -> {
            try {
                log.info("[배당배치] 부팅 동기화 시작");
                dividendBatchService.syncDividendEvents();
            } catch (Exception e) {
                log.error("[배당배치] 부팅 동기화 실패 — {}", e.getMessage(), e);
            }
        }, "dividend-boot-sync");
        t.setDaemon(true);
        t.start();
    }
}
