package com.pocketstock.ledger.trading.controller;

import com.pocketstock.ledger.trading.mapper.MaturityReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * core→ledger 내부 호출 전용 — 만기 굴리기 보조 조회.
 * 인증은 @CurrentUserId 대신 파라미터 userId (다른 /internal/* 와 동일 패턴).
 */
@RestController
@RequestMapping("/internal/maturity")
@RequiredArgsConstructor
public class InternalMaturityController {

    private final MaturityReservationMapper reservationMapper;

    /**
     * 활성 예약(RESERVED·EXECUTED) 계좌 id — core가 만기 굴리기 후보 목록에서 제외하는 데 사용.
     * 이미 자금 굴리기로 선택한 예적금은 후보에서 빠지고 전환내역(예약 목록)에만 노출된다.
     */
    @GetMapping("/reserved-account-ids")
    public List<Long> reservedAccountIds(@RequestParam Long userId) {
        return reservationMapper.findActiveReservedAccountIds(userId);
    }
}
