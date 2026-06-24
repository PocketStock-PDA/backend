package com.pocketstock.ledger.trading.dto;

import com.pocketstock.ledger.trading.domain.AutoInvestExecution;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 자동모으기 회차 1건 응답 (모으기 내역 화면). 체결(FILLED)은 금액·수량, 실패(FAILED)는 사유.
 */
public record AutoInvestExecutionResponse(
        Long id,
        Integer roundNo,
        String triggerSource,
        String side,
        LocalDate execDate,
        String status,
        String failReason,
        Long orderId,
        BigDecimal execAmount,
        BigDecimal execQuantity,
        String currency
) {
    public static AutoInvestExecutionResponse from(AutoInvestExecution e) {
        return new AutoInvestExecutionResponse(e.getId(), e.getRoundNo(), e.getTriggerSource(), e.getSide(),
                e.getExecDate(), resolveStatus(e), e.getFailReason(), e.getOrderId(),
                e.getExecAmount(), e.getExecQuantity(), e.getCurrency());
    }

    /**
     * 라이브 상태 — 추적 주문(order_id)이 차수에서 체결/거부되면 그 결과를 반영한다(접수 스냅샷 status 위에 덮어씀).
     * 주문이 없으면(접수 실패) 저장된 status(FAILED) 그대로.
     */
    private static String resolveStatus(AutoInvestExecution e) {
        String os = e.getOrderStatus();
        if (os == null) {
            return e.getStatus();   // 접수 실패(order 미생성) 등
        }
        return switch (os) {
            case "FILLED" -> "FILLED";
            case "REJECTED" -> "REJECTED";
            case "CANCELLED" -> "CANCELLED";
            default -> "QUEUED";    // RECEIVED/QUEUED/SENT/PENDING = 차수 대기 중
        };
    }
}
