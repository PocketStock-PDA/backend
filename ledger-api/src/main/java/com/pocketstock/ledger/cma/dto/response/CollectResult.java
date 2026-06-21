package com.pocketstock.ledger.cma.dto.response;

import java.math.BigDecimal;

/**
 * 잔돈 수집 실행 결과 (소스 1종 단위).
 *
 * <p>통합 수집({@code POST /api/cma/collect})은 소스별로 독립 실행하므로(E-1, 부분 성공 허용)
 * 응답에 소스별 결과를 모아 반환한다. 단건 API는 단일 {@code CollectResult}를 반환한다.
 *
 * @param status SUCCESS(적립됨) / SKIPPED(소스 비활성·수집 잔돈 없음) / FAILED(예기치 못한 오류)
 */
public record CollectResult(
        String sourceType,      // ACCOUNT / CARD / POINT
        String status,
        BigDecimal amount,      // 적립 금액 (SKIPPED/FAILED는 null 또는 0)
        BigDecimal balanceAfter,
        String errorMessage
) {
    public static CollectResult success(String sourceType, BigDecimal amount, BigDecimal balanceAfter) {
        return new CollectResult(sourceType, "SUCCESS", amount, balanceAfter, null);
    }

    /** 소스 비활성·수집 가능 잔돈 없음 등 정상 범주의 건너뜀(부분 성공). */
    public static CollectResult skipped(String sourceType, String reason) {
        return new CollectResult(sourceType, "SKIPPED", BigDecimal.ZERO, null, reason);
    }

    /** 예기치 못한 오류(Feign 장애 등). */
    public static CollectResult failed(String sourceType, String errorMessage) {
        return new CollectResult(sourceType, "FAILED", null, null, errorMessage);
    }
}
