package com.pocketstock.core.internal.holding;

import com.pocketstock.core.internal.holding.dto.HoldingReplicaUpsertRequest;
import com.pocketstock.core.internal.holding.mapper.HoldingReplicaMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ledger-api로부터 보유 스냅샷을 받아 {@code holdings_replica}(DB A)에 동기화한다.
 * 증권 캘린더가 보유 종목으로 일정을 필터하는 근거 테이블.
 *
 * <p>upsert는 비파괴(스냅샷에 없는 행을 지우지 않아 시드 보존). 전량 매도처럼 더는 안 가진 종목은
 * ledger가 체결 이벤트로 {@link #deleteReplica} 를 호출해 정밀 삭제한다(판 종목 일정이 캘린더에 남지 않도록).
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

    /** 전량 매도 종목 복제행 삭제 — ledger가 SELL 체결 후 잔량 0이면 호출. */
    @DeleteMapping("/replica/{userId}/{stockCode}")
    public void deleteReplica(@PathVariable Long userId, @PathVariable String stockCode) {
        holdingReplicaMapper.deleteReplica(userId, stockCode);
    }
}
