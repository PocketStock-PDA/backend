package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.ledger.client.MaturityDepositFeignClient;
import com.pocketstock.ledger.client.dto.DueCmaTransferView;
import com.pocketstock.ledger.cma.port.CmaChargePort;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import com.pocketstock.ledger.trading.domain.MaturityBuyReservation;
import com.pocketstock.ledger.trading.mapper.MaturityReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final MaturityDepositFeignClient maturityDepositFeignClient;
    private final CmaChargePort cmaChargePort;
    private final LedgerActivation activation;

    /** 만기 매수 집행 — 매일 09:10 KST(국내 개장 직후). cron·dev 수동 트리거 공용 진입점. */
    @Scheduled(cron = "0 10 9 * * *", zone = "Asia/Seoul")
    public void run() {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 활성 색이 집행.
        }
        LocalDate today = LocalDate.now(KST);
        // 이번 회차에 만기 이자를 입금한 계좌 — 배당주 매수·CMA 이체 집행 전에 계좌별 1회만 입금(중복 Feign 방지).
        Set<Long> interestSettled = new HashSet<>();
        List<MaturityBuyReservation> due = reservationMapper.findDue(today);
        log.info("[만기매수] 집행 시작 — 도래 예약 {}건 ({})", due.size(), today);
        for (MaturityBuyReservation r : due) {
            try {
                // 매수 전 만기 이자 입금 — 잔액=총 수령액(원금+이자)이 돼야 총 수령액 기준 배분이 잔액부족 없이 집행된다.
                settleInterest(r.getUserId(), r.getLinkedBankAccountId(), interestSettled);
                executeOne(r);
            } catch (Exception e) {
                // executeOne 내부에서 못 잡은 시스템 오류 — 다음 예약으로 계속.
                log.error("[만기매수] 예약 {} 집행 실패(stock={})", r.getId(), r.getStockCode(), e);
            }
        }
        runDueCmaTransfers(today, interestSettled);
    }

    /**
     * 만기 이자 입금(계좌별 1회) — core가 멱등 처리(interest_credited_at)하므로 안전. 성공 시에만 회차 set에 기록해
     * 실패한 계좌는 같은 회차의 다른 예약에서 재시도하게 둔다. 실패는 호출자 try가 받아 해당 건만 건너뛴다.
     */
    private void settleInterest(Long userId, Long accountId, Set<Long> settled) {
        if (accountId == null || settled.contains(accountId)) {
            return;
        }
        maturityDepositFeignClient.settleInterest(accountId, userId);
        settled.add(accountId);
    }

    /**
     * 만기일 'CMA 이체' 집행 — '재예치 없이 CMA로'를 고른 예약을 만기일에 계좌→CMA로 옮긴다(배당주 매수와 동일 타이밍).
     * 예약 레코드는 core(DB A) 소유라 Feign으로 도래분을 받아 ledger 공용 charge 포트로 집행하고, 결과를 core에 마킹한다.
     * 건별 격리 — 한 건 실패가 배치를 멈추지 않는다. 멱등키 {@code MATURITY_REMAINDER:{id}}로 중복 이체 차단.
     */
    private void runDueCmaTransfers(LocalDate today, Set<Long> interestSettled) {
        List<DueCmaTransferView> dueCma;
        try {
            dueCma = maturityDepositFeignClient.getDueCmaTransfers(today);
        } catch (Exception e) {
            log.error("[만기CMA이체] 도래분 조회 실패 — 이번 회차 건너뜀", e);
            return;
        }
        log.info("[만기CMA이체] 집행 시작 — 도래 {}건 ({})", dueCma.size(), today);
        for (DueCmaTransferView t : dueCma) {
            try {
                // 이체 전 만기 이자 입금 — 배당주 매수와 동일하게 잔액=총 수령액이 되도록(계좌별 1회, 멱등).
                settleInterest(t.userId(), t.linkedBankAccountId(), interestSettled);
                cmaChargePort.charge(t.userId(), t.linkedBankAccountId(), t.amount(),
                        "MATURITY_REMAINDER:" + t.id());
                maturityDepositFeignClient.markStatus(t.id(), STATUS_EXECUTED);
                log.info("[만기CMA이체] {} 집행 완료 — 계좌 {} {}원 → CMA",
                        t.id(), t.linkedBankAccountId(), t.amount());
            } catch (Exception e) {
                try {
                    maturityDepositFeignClient.markStatus(t.id(), STATUS_FAILED);
                } catch (Exception ignore) {
                    // 마킹 실패는 무시 — 다음 회차에 RESERVED로 재시도(멱등키가 이중 이체 차단).
                }
                log.warn("[만기CMA이체] {} 집행 실패 — {}", t.id(), e.getMessage());
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
