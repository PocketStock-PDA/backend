package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.outbox.OutboxEventPublisher;
import com.pocketstock.ledger.outbox.event.AutoInvestExecutedEvent;
import com.pocketstock.ledger.trading.domain.AutoInvestExecution;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.mapper.AutoInvestExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 자동모으기 회차 로그(auto_invest_executions) 적재 공용 — 정기매수(스케줄러)·트리거(평가기)가 함께 쓴다.
 * 접수 스냅샷 status: 소수부 있으면 QUEUED(차수 대기), 온주 즉시 전량만 FILLED, 접수 실패 FAILED.
 * 조회 시 order_id로 orders 상태를 라이브 파생(소수부 :F 우선 추적).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoInvestExecutionRecorder {

    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final int REASON_MAX = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AutoInvestExecutionMapper executionMapper;
    private final OutboxEventPublisher outboxPublisher;

    /** 종목별 다음 회차 = max+1. */
    public int nextRoundNo(Long autoInvestStockId) {
        Integer max = executionMapper.findMaxRoundNo(autoInvestStockId);
        return max == null ? 1 : max + 1;
    }

    /** 접수 성공 — 회차 로그 + 자동모으기 알림 이벤트(같은 커밋, #204). 소수부=QUEUED·온주즉시=FILLED. */
    @Transactional
    public void recordAccepted(Long userId, Long stockId, String stockCode, int roundNo, LocalDate today,
                               String triggerSource, String side, String currency, SplitOrderResponse resp) {
        String status = resp.fractionalOrderId() != null ? STATUS_QUEUED : STATUS_FILLED;
        BigDecimal qty = nz(resp.wholeQty() == null ? null : BigDecimal.valueOf(resp.wholeQty()))
                .add(nz(resp.fractionalEstQty()));
        BigDecimal amount = nz(resp.wholeAmount()).add(nz(resp.fractionalHeld()));
        Long orderId = resp.fractionalOrderId() != null ? resp.fractionalOrderId() : resp.wholeOrderId();
        save(AutoInvestExecution.builder()
                .autoInvestStockId(stockId).roundNo(roundNo).triggerSource(triggerSource).side(side)
                .execDate(today).status(status).orderId(orderId).execAmount(amount).execQuantity(qty)
                .currency(currency).build());
        publishEvent(userId, stockId, stockCode, roundNo, triggerSource, side,
                STATUS_ACCEPTED, amount, qty, null, currency);
    }

    /** 접수 실패(잔액부족 등) — 회차 로그(FAILED) + 실패 알림 이벤트(같은 커밋). */
    @Transactional
    public void recordFailed(Long userId, Long stockId, String stockCode, int roundNo, LocalDate today,
                             String triggerSource, String side, String currency, String reason) {
        String trimmed = reason == null ? "" : (reason.length() > REASON_MAX ? reason.substring(0, REASON_MAX) : reason);
        save(AutoInvestExecution.builder()
                .autoInvestStockId(stockId).roundNo(roundNo).triggerSource(triggerSource).side(side)
                .execDate(today).status(STATUS_FAILED).failReason(trimmed).currency(currency).build());
        publishEvent(userId, stockId, stockCode, roundNo, triggerSource, side,
                STATUS_FAILED, null, null, trimmed, currency);
    }

    /** 자동모으기 알림 이벤트 — outbox(autoinvest.executed). 회차당 1건(eventId=autoinvest:exec:{stockId}:{roundNo}). */
    private void publishEvent(Long userId, Long stockId, String stockCode, int roundNo, String triggerSource,
                              String side, String status, BigDecimal amount, BigDecimal qty, String failReason,
                              String currency) {
        String eventId = "autoinvest:exec:" + stockId + ":" + roundNo;
        AutoInvestExecutedEvent event = new AutoInvestExecutedEvent(eventId, userId, stockCode, triggerSource,
                side, status, amount, qty, failReason, currency, LocalDateTime.now(KST).toString());
        outboxPublisher.publish(AutoInvestExecutedEvent.TOPIC, eventId, AutoInvestExecutedEvent.AGGREGATE,
                stockId, event);
    }

    /** (stock, round_no) UNIQUE라 멀티인스턴스 중복이면 한쪽만 성공(나머지 무시). */
    private void save(AutoInvestExecution execution) {
        try {
            executionMapper.insert(execution);
        } catch (DuplicateKeyException e) {
            log.debug("[자동모으기] 회차 로그 중복 — stockId={} round={}",
                    execution.getAutoInvestStockId(), execution.getRoundNo());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
