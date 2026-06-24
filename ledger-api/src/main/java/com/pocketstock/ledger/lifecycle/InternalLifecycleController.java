package com.pocketstock.ledger.lifecycle;

import com.pocketstock.ledger.exchange.realtime.CurrencyRatePinner;
import com.pocketstock.ledger.kis.KisRealtimeClient;
import com.pocketstock.ledger.ls.LsRealtimeClient;
import com.pocketstock.ledger.trading.matching.WholeOrderMatchingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 무중단 배포(Blue-Green) 핸드오버 제어 — deploy-release.sh 가 색 전환 시 호출하는 내부 엔드포인트.
 * nginx 는 {@code /internal} 을 외부에 노출하지 않으므로(게이트웨이 404), EC2 에서 컨테이너 내부로
 * {@code docker exec ... curl localhost:8082/internal/lifecycle/...} 로만 호출된다(인증 불요).
 *
 * <p>핸드오버 순서(스크립트): nginx 스위치 → 옛 색 {@code /quiesce} → drain → 옛 색 정지 →
 * active-color 전환 → 새 색 {@code /rearm}. quiesce→정지가 rearm 보다 먼저라 "동시 1개만 활성"이
 * 보장되고 LS/KIS 세션도 겹치지 않는다.
 */
@Slf4j
@RestController
@RequestMapping("/internal/lifecycle")
@RequiredArgsConstructor
public class InternalLifecycleController {

    private final LedgerActivation activation;
    private final CurrencyRatePinner currencyRatePinner;
    private final WholeOrderMatchingEngine matchingEngine;
    private final LsRealtimeClient lsRealtimeClient;
    private final KisRealtimeClient kisRealtimeClient;

    /** 옛 색 드레인 시작 — 백그라운드 즉시 비활성(진행 틱 마무리). 배포 스크립트가 정지 전 호출. */
    @PostMapping("/quiesce")
    public Map<String, Object> quiesce() {
        activation.startDraining();
        return Map.of("color", activation.myColor(), "draining", true);
    }

    /**
     * 새 색 활성 인계 — active-color 캐시 동기화 후 백그라운드 재무장.
     * ① 매칭 인덱스를 DB 로부터 재적재(옛 색에 들어온 PENDING 포함 → 고아 방지) + 구독·스냅샷
     * ② CUR 상시구독 재개 ③ 보류했던 LS/KIS 등록 일괄 재전송.
     */
    @PostMapping("/rearm")
    public Map<String, Object> rearm() {
        activation.refreshNow();
        if (!activation.isActive()) {
            log.warn("[Blue-Green] rearm 요청됐으나 active-color 가 이 색({})이 아님 — 스킵", activation.myColor());
            return Map.of("color", activation.myColor(), "active", false,
                    "msg", "active-color mismatch — rearm skipped");
        }
        matchingEngine.reindexAndArm();
        currencyRatePinner.pin();
        lsRealtimeClient.rearm();
        kisRealtimeClient.rearm();
        log.info("[Blue-Green] color={} rearm 완료 — 백그라운드 활성", activation.myColor());
        return Map.of("color", activation.myColor(), "active", true);
    }
}
