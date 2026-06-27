package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import com.pocketstock.ledger.trading.domain.MaturityBuyReservation;
import com.pocketstock.ledger.trading.mapper.MaturityReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 만기 후 배당주 매수 예약 집행기 — 만기일이 도래한 예약을 모아 만기 원금으로 배당주를 매수한다.
 *
 * <p>국내 09:10 KST(개장 직후, 장중 보장 — 자동모으기 정기매수와 동일 시각). {@code status=RESERVED} &
 * {@code maturity_date ≤ today} 예약을 건별 격리 집행한다(한 건 실패가 배치를 멈추지 않게). 실제 매수·충전은
 * {@link MaturityReservationService#executeReservation}(한 트랜잭션)이 맡고, 결과만 여기서 EXECUTED/FAILED로 마킹한다.
 *
 * <p>멱등: 충전키·client_order_id가 결정적이라 재시도해도 중복 충전·매수 없음. 멀티인스턴스는 단일 활성
 * 게이트({@link LedgerActivation})로 한 대만 집행(자동모으기 {@code AutoInvestScheduler}와 동형).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaturityReservationScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String STATUS_EXECUTED = "EXECUTED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int FAIL_REASON_MAX = 100;   // fail_reason VARCHAR(100)

    private final MaturityReservationMapper reservationMapper;
    private final MaturityReservationService reservationService;
    private final LedgerActivation activation;

    /** 만기 매수 집행 — 매일 09:10 KST(국내 개장 직후). cron·dev 수동 트리거 공용 진입점. */
    @Scheduled(cron = "0 10 9 * * *", zone = "Asia/Seoul")
    public void run() {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 활성 색이 집행.
        }
        LocalDate today = LocalDate.now(KST);
        List<MaturityBuyReservation> due = reservationMapper.findDue(today);
        log.info("[만기매수] 집행 시작 — 도래 예약 {}건 ({})", due.size(), today);
        for (MaturityBuyReservation r : due) {
            try {
                executeOne(r);
            } catch (Exception e) {
                // executeOne 내부에서 못 잡은 시스템 오류 — 다음 예약으로 계속.
                log.error("[만기매수] 예약 {} 집행 실패(stock={})", r.getId(), r.getStockCode(), e);
            }
        }
    }

    /** 예약 1건 집행 + 결과 마킹. 매수/충전은 자체 트랜잭션(executeReservation), 마킹은 별도 — 실패도 FAILED로 남긴다. */
    private void executeOne(MaturityBuyReservation r) {
        try {
            Long orderId = reservationService.executeReservation(r);
            reservationMapper.markExecuted(r.getId(), STATUS_EXECUTED, orderId, null);
            log.info("[만기매수] 예약 {} 집행 완료 — {} {}원 (orderId={})",
                    r.getId(), r.getStockCode(), r.getBuyAmount(), orderId);
        } catch (BusinessException e) {
            // 잔액부족·계좌해지 등 비즈니스 실패 = 매수 트랜잭션 롤백(부분반영 없음) → 예약을 FAILED로.
            reservationMapper.markExecuted(r.getId(), STATUS_FAILED, null, truncate(e.getMessage()));
            log.warn("[만기매수] 예약 {} 집행 실패 — {} ({})", r.getId(), r.getStockCode(), e.getMessage());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= FAIL_REASON_MAX ? message : message.substring(0, FAIL_REASON_MAX);
    }
}
