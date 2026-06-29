package com.pocketstock.ledger.client;

import com.pocketstock.ledger.client.dto.DueCmaTransferView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * ledger→core 내부 호출 — 만기일 'CMA 이체' 예약 집행 보조.
 * 만기 굴리기에서 '재예치 없이 CMA로'를 고른 예약을 만기 스케줄러가 집행할 때 사용한다.
 */
@FeignClient(name = "core-api-maturity-deposit", url = "${feign.core-api.url}")
public interface MaturityDepositFeignClient {

    /** 만기 도래한 CMA 이체 예약 목록(RESERVED, maturity ≤ date). */
    @GetMapping("/internal/maturity-deposit/due-cma")
    List<DueCmaTransferView> getDueCmaTransfers(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);

    /** CMA 이체 예약 상태 갱신(EXECUTED/FAILED). */
    @PostMapping("/internal/maturity-deposit/{id}/status")
    void markStatus(@PathVariable("id") Long id, @RequestParam("status") String status);

    /** 만기 이자 입금(1회, 멱등) — 만기 굴리기 집행 직전 호출. 잔액을 원금+만기이자로 만든다. */
    @PostMapping("/internal/maturity-deposit/account/{accountId}/settle-interest")
    void settleInterest(@PathVariable("accountId") Long accountId, @RequestParam("userId") Long userId);
}
