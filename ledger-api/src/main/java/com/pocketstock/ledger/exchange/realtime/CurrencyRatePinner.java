package com.pocketstock.ledger.exchange.realtime;

import com.pocketstock.ledger.ls.LsRealtimeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 환율(LS CUR) **상시구독** — 종목 시세와 달리 환율은 아무도 티커를 안 봐도
 * REST·환전 체결이 항상 필요하므로 온디맨드가 아니라 서버 기동 시 1번 등록(pin)한다.
 *
 * <p>구독 누수·세션 끊김에 대비해 주기 하트비트로 재등록을 시도한다.
 * {@link LsRealtimeClient#register}는 멱등(이미 등록된 키면 no-op)이고,
 * 세션이 끊겼다면 내부에서 재연결+등록종목 복구를 수행한다.
 *
 * <p>온디맨드 경로({@code RealtimeSubscriptionManager})는 CUR을 다루지 않는다 —
 * 여기서만 등록/유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyRatePinner {

    private static final String TR_CD = "CUR";
    /** LS CUR tr_key = 통화코드(USD) 공백 패딩 8자리 — 명세 "USD     ". */
    private static final String TR_KEY = String.format("%-8s", "USD");

    private final LsRealtimeClient lsClient;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        pin();
    }

    /** 1분마다 상시구독 유지(끊겼으면 재연결+재등록). */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void heartbeat() {
        pin();
    }

    private void pin() {
        try {
            lsClient.register(TR_CD, TR_KEY);
        } catch (Exception e) {
            // LS 미연결(로컬 시크릿 없음 등) — 다음 하트비트에서 재시도.
            log.warn("환율(CUR) 상시구독 실패 — 다음 주기 재시도: {}", e.getMessage());
        }
    }
}
