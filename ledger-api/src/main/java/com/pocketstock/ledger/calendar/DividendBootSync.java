package com.pocketstock.ledger.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 배당 일정 1회 동기화 — 정기 크론(국내 {@link DividendBatchService#syncDividendEvents()} 1월 1일 02시,
 * 해외 {@link OverseasDividendBatchService#syncOverseasDividends()} 매주 월 02:30)을 기다리지 않고 앱이 뜰
 * 때마다 향후 배당락일·지급일·주당배당금을 즉시 채운다. 국내(KIS 예탁원)·해외(야후 chart) 둘 다 돌린다.
 *
 * <p>배치 자체가 단일활성 게이트({@code LedgerActivation.isActive()})로 보호되어, Blue-Green에선 활성 색만
 * 실행하고 비활성 색은 skip한다(중복 upsert 없음).
 *
 * <p>{@code ApplicationReadyEvent} 리스너는 동기라, 전체 종목 배당 조회가 부팅 완료를 잡아두지 않도록
 * {@link BootSyncRetry}가 데몬 스레드에서 비동기로 돌린다. ledger가 core-api보다 먼저 떠 첫 upsert가
 * 실패하면 30초 간격으로 최대 10회(약 5분) 재시도하므로, 기동 순서와 무관하게 core-api가 그 안에 뜨면 채워진다.
 */
@Component
@RequiredArgsConstructor
public class DividendBootSync {

    private final DividendBatchService dividendBatchService;
    private final OverseasDividendBatchService overseasDividendBatchService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        BootSyncRetry.runAsync("dividend-boot-sync", "[배당배치]", 10, 30_000L,
                dividendBatchService::syncDividendEvents);
        BootSyncRetry.runAsync("overseas-dividend-boot-sync", "[해외배당]", 10, 30_000L,
                overseasDividendBatchService::syncOverseasDividends);
    }
}
