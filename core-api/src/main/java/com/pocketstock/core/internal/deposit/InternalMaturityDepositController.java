package com.pocketstock.core.internal.deposit;

import com.pocketstock.core.recommendations.deposit.DepositService;
import com.pocketstock.core.recommendations.deposit.dto.DueCmaTransfer;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * ledger→core 내부 호출 — 만기일 'CMA 이체' 예약 집행 보조.
 * 만기 굴리기에서 '재예치 없이 CMA로'를 고른 예약을, ledger 만기 스케줄러가 만기일에 집행할 때 사용한다.
 */
@RestController
@RequestMapping("/internal/maturity-deposit")
@RequiredArgsConstructor
public class InternalMaturityDepositController {

    private final DepositService depositService;

    /** 만기 도래한 CMA 이체 예약 목록 — 스케줄러 집행 대상. */
    @GetMapping("/due-cma")
    public List<DueCmaTransfer> dueCmaTransfers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return depositService.listDueCmaTransfers(date);
    }

    /** CMA 이체 예약 상태 갱신(EXECUTED/FAILED) — 스케줄러 집행 결과 반영. */
    @PostMapping("/{id}/status")
    public void markStatus(@PathVariable Long id, @RequestParam String status) {
        depositService.markRolloverStatus(id, status);
    }

    /** 만기 이자 입금(1회, 멱등) — 만기 굴리기 집행 직전 계좌별 호출. 잔액=원금+만기이자로 만든다. */
    @PostMapping("/account/{accountId}/settle-interest")
    public void settleInterest(@PathVariable Long accountId, @RequestParam Long userId) {
        depositService.settleMaturityInterest(userId, accountId);
    }
}
