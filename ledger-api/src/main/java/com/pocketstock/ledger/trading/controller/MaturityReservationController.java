package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.MaturityReservationRequest;
import com.pocketstock.ledger.trading.dto.MaturityReservationResponse;
import com.pocketstock.ledger.trading.service.MaturityReservationService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 만기 후 배당주 매수 예약 — 예적금 만기일에 추천 배당주를 자동 매수하도록 예약.
 * 등록 자체가 자동 매수 사전동의(자동모으기와 동일) — 실제 매수는 {@code MaturityReservationScheduler}가
 * 만기일 09:10 KST에 {@code source=MATURITY}로 집행한다.
 */
@RestController
@RequestMapping("/api/trading/maturity-reservations")
@RequiredArgsConstructor
public class MaturityReservationController {

    private final MaturityReservationService maturityReservationService;

    /** 예약 생성 — 만기 계좌·매수금액·배당주(국내). 만기일·시장·통화는 서버가 계좌·종목에서 파생. */
    @PostMapping
    public ApiResponse<MaturityReservationResponse> create(@CurrentUserId Long userId,
                                                           @RequestBody MaturityReservationRequest request) {
        return ApiResponse.ok("만기 매수 예약 성공", maturityReservationService.create(userId, request));
    }

    /** 내 예약 목록(최신순). */
    @GetMapping
    public ApiResponse<List<MaturityReservationResponse>> list(@CurrentUserId Long userId) {
        return ApiResponse.ok("만기 매수 예약 조회 성공", maturityReservationService.list(userId));
    }

    /** 예약 취소(만기 전 RESERVED 상태만). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@CurrentUserId Long userId, @PathVariable Long id) {
        maturityReservationService.cancel(userId, id);
        return ApiResponse.ok("만기 매수 예약 취소 성공", null);
    }
}
