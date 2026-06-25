package com.pocketstock.ledger.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 실적 일정 1회 동기화 — 주간 크론({@link EarningsBatchService#syncEarningsEvents()}, 월 03시)을
 * 기다리지 않고 앱이 뜰 때마다 보유 종목의 최근 잠정실적을 즉시 채운다.
 *
 * <p>모든 환경에서 동작하나 배치 자체가 단일활성 게이트({@code LedgerActivation.isActive()})로 보호되어,
 * Blue-Green에선 활성 색만 실행하고 비활성 색은 skip한다(중복 upsert 없음). 보유 종목이 없으면 배치가
 * 내부에서 skip한다.
 *
 * <p>{@code ApplicationReadyEvent} 리스너는 동기라, 보유 종목 수 × DART 호출(throttle 120ms)이 부팅
 * 완료를 잡아두지 않도록 데몬 스레드에서 비동기로 돌린다. core-api가 아직 안 떴으면 그 회차 upsert는
 * 실패해 로그만 남고(크래시 아님), 주간 크론이 백업한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EarningsBootSync {

    private final EarningsBatchService earningsBatchService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread t = new Thread(() -> {
            try {
                log.info("[실적배치] 부팅 동기화 시작");
                earningsBatchService.syncEarningsEvents();
            } catch (Exception e) {
                log.error("[실적배치] 부팅 동기화 실패 — {}", e.getMessage(), e);
            }
        }, "earnings-boot-sync");
        t.setDaemon(true);
        t.start();
    }
}
