package com.pocketstock.ledger.trading.matching;

import com.pocketstock.ledger.trading.domain.TradingRound;
import com.pocketstock.ledger.trading.mapper.RoundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 소수점 1분 차수 스케줄러(#152) — 매 분 실행시각이 도달한 OPEN 차수를 골라 배치 집행기로 넘긴다.
 * 차수 생성은 접수({@link com.pocketstock.ledger.trading.service.FractionalOrderService})가 find-or-create로
 * 하고(주문 없는 분은 차수도 없음), 여긴 집행 트리거만 맡는다(D5: 장외·휴장 구분 없이 24/7 상시).
 *
 * <p><b>멀티 인스턴스 단일 실행(D3)</b>: N대 모두 이 스케줄을 돌리되 {@code claimForExecution}의
 * 조건부 UPDATE(OPEN→EXECUTING)로 차수를 선점한다 — affected=1인 인스턴스만 집행해 이중집행을 DB가 차단.
 * 별도 분산락 불필요. 집행 성공 시 SETTLED, 예외 시 FAILED(복구스윕/모니터링 대상).
 *
 * <p>매 분 <b>5초</b>에 실행 — 분 경계({@code execute_at}=분끝)에 막 들어온 접수와의 경합을 피하는 그레이스.
 * 접수는 항상 현재 분 차수로 들어가므로(다음 분 주문은 다음 차수), 5초 뒤 집행 시 직전 분 차수는 닫혀 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FractionalRoundScheduler {

    /** EXECUTING이 이 분(分) 이상 정체면 사망 인스턴스로 보고 회수(정상 집행은 수초). */
    private static final long STALE_MINUTES = 5;

    private final RoundMapper roundMapper;
    private final FractionalBatchService batchService;

    @Scheduled(cron = "5 * * * * *")
    public void runDueRounds() {
        LocalDateTime now = LocalDateTime.now();
        // 복구스윕 — 인스턴스 사망 등으로 정체된 EXECUTING 차수를 OPEN으로 회수(재집행은 QUEUED만 처리 → 멱등).
        int reopened = roundMapper.reopenStalled(now.minusMinutes(STALE_MINUTES));
        if (reopened > 0) {
            log.warn("[소수점배치] 정체 EXECUTING 차수 {}건 회수(OPEN 복귀) — 재집행 예정", reopened);
        }
        List<TradingRound> due = roundMapper.findDueOpenRounds(now);
        for (TradingRound round : due) {
            // 차수 선점 — affected=0이면 다른 인스턴스가 이미 가져감(이중집행 차단).
            if (roundMapper.claimForExecution(round.getId()) == 0) {
                continue;
            }
            try {
                batchService.executeRound(round);
                roundMapper.markSettled(round.getId());
            } catch (Exception e) {
                // 집행 트랜잭션은 롤백됨 — 차수만 FAILED로 마감(복구스윕/모니터링이 회수).
                log.error("[소수점배치] 차수 집행 실패 roundId={} — FAILED 처리", round.getId(), e);
                roundMapper.markFailed(round.getId());
            }
        }
    }
}
