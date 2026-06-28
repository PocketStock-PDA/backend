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
 * <p>전체 활성 보유를 통째로 upsert한다(삭제 없음). 매도로 보유가 0이 된 종목의 복제행은 남지만,
 * 캘린더 표시상 무해(있는 종목으로 한정해 일정만 더 보일 뿐)하고 시드 데이터를 지우지 않는다.
 * 단일활성 게이트({@code LedgerActivation.isActive()})로 Blue-Green 비활성 색은 skip한다.
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
}
