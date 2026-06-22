package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 휴면계좌 일괄 해지 결과.
 *
 * <p>{@code closedCount}·{@code transferredAmount}는 이번 호출에서 새로 완료된({@code COMPLETED}) 건만 집계한다.
 * 과거 호출로 이미 해지된 계좌는 {@code ALREADY_CLOSED}로 집계에서 빠진다. {@code allCompleted}가 false여도
 * UI는 계좌별 {@code results[].status}로 부분 성공을 표시한다.
 */
public record DormantCloseResponse(
        int closedCount,
        BigDecimal transferredAmount,
        boolean allCompleted,
        List<Result> results
) {
    /** 계좌별 처리 결과. status = COMPLETED | ALREADY_CLOSED | FAILED. */
    public record Result(
            Long accountId,
            BigDecimal amount,
            String currency,
            String status
    ) {
    }
}
