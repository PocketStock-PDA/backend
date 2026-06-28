package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.client.HoldingReplicaFeignClient;
import com.pocketstock.ledger.client.dto.HoldingReplicaUpsertRequest;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 보유 스냅샷을 core-api {@code holdings_replica}로 동기화한다 — 증권 캘린더가 보유 종목으로
 * 일정을 필터하는 근거. ledger(DB B) holdings → core-api(DB A) holdings_replica upsert(비파괴).
 *
 * <p>주기/부팅 동기화는 전체 활성 보유를 통째로 upsert한다(스냅샷에 없는 행 삭제 안 함 → 시드 보존).
 * 전량 매도로 더는 안 가진 종목은 {@link #removeIfSoldOut}가 매도 체결 이벤트를 받아 정밀 삭제한다
 * (안 가진 종목 일정이 캘린더에 남지 않도록). 단일활성 게이트({@code LedgerActivation.isActive()})로
 * Blue-Green 비활성 색은 skip한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingReplicaSyncService {

    private final HoldingMapper holdingMapper;
    private final HoldingReplicaFeignClient replicaClient;
    private final LedgerActivation activation;

    /** 주기 동기화 — 10분마다. 부팅 1회는 {@link HoldingReplicaBootSync}가 담당. */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 600_000L)
    public void scheduledSync() {
        sync();
    }

    public void sync() {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 활성 색이 동기화.
        }
        List<Holding> holdings = holdingMapper.findAllActive();
        if (holdings.isEmpty()) {
            log.info("[보유복제] 활성 보유 없음 — skip");
            return;
        }
        List<HoldingReplicaUpsertRequest> rows = holdings.stream()
                .map(h -> new HoldingReplicaUpsertRequest(
                        h.getUserId(), h.getStockCode(), h.getQuantity(), h.getAvgBuyPrice(), h.getCurrency()))
                .toList();
        try {
            replicaClient.upsertReplica(rows);
            log.info("[보유복제] {}건 동기화 완료", rows.size());
        } catch (Exception e) {
            log.error("[보유복제] core-api 동기화 실패 — {}건: {}", rows.size(), e.getMessage());
        }
    }

    /**
     * 전량 매도된 종목을 core-api {@code holdings_replica}에서 제거한다 — 매도 체결 이벤트가 호출.
     * upsert는 스냅샷에 없는 행을 안 지우므로(시드 보존), 안 가진 종목 일정이 캘린더에 남는 걸 이 경로가 정밀 삭제로 막는다.
     *
     * <p>잔량 판정은 ledger 보유(DB B)를 직접 조회: 행이 없거나 quantity ≤ 0이면 전량 매도. 일부 매도(잔량 남음)는 무시.
     * Feign 삭제 실패는 호출자(체결 컨슈머)가 재시도하도록 전파한다. 비활성 색은 skip.
     */
    public void removeIfSoldOut(Long userId, String stockCode) {
        if (!activation.isActive()) {
            return;
        }
        Holding h = holdingMapper.findByUserIdAndStock(userId, stockCode);
        boolean soldOut = h == null || h.getQuantity() == null || h.getQuantity().signum() <= 0;
        if (!soldOut) {
            return;   // 잔량 남음 — 유지
        }
        replicaClient.deleteReplica(userId, stockCode);   // 실패 전파 → 상위 재시도
        log.info("[보유복제] 전량매도 — user={} {} replica 제거", userId, stockCode);
    }
}
