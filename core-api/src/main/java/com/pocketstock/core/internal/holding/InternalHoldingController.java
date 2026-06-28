package com.pocketstock.core.internal.holding;

import com.pocketstock.core.internal.holding.dto.HoldingReplicaUpsertRequest;
import com.pocketstock.core.internal.holding.mapper.HoldingReplicaMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ledger-api로부터 보유 스냅샷을 받아 {@code holdings_replica}(DB A)에 upsert한다.
 * 증권 캘린더가 보유 종목으로 일정을 필터하는 근거 테이블. 비파괴(삭제 없음)라 시드 데이터를 지우지 않는다.
 */
@RestController
@RequestMapping("/internal/holdings")
@RequiredArgsConstructor
public class InternalHoldingController {

    private final HoldingReplicaMapper holdingReplicaMapper;

    @PostMapping("/replica")
    @Transactional
    public void upsertReplica(@RequestBody @Valid List<HoldingReplicaUpsertRequest> rows) {
        for (HoldingReplicaUpsertRequest r : rows) {
            holdingReplicaMapper.upsertReplica(
                    r.userId(), r.stockCode(), r.quantity(), r.avgBuyPrice(), r.currency());
        }
    }
}
