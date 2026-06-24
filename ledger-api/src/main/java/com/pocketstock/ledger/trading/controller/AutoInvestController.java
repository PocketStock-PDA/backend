package com.pocketstock.ledger.trading.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestExecutionResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestOverviewResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestRequest;
import com.pocketstock.ledger.trading.dto.AutoInvestResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestStatusRequest;
import com.pocketstock.ledger.trading.service.AutoInvestService;
import com.pocketstock.user.security.CurrentUserId;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자동모으기(정기적립) 설정 — 주기 base. 트리거(물타기/익절)는 {@code /auto-invest/{id}/triggers}로 별도.
 * 등록 자체가 자동 매수 사전동의 — 실제 매수는 AutoInvestScheduler가 source=AUTO로 집행.
 */
@RestController
@RequestMapping("/api/trading/auto-invest")
@RequiredArgsConstructor
public class AutoInvestController {

    private final AutoInvestService autoInvestService;

    /** 자동모으기 종목 등록(주기/금액·수량). */
    @PostMapping
    public ApiResponse<AutoInvestResponse> register(@CurrentUserId Long userId,
                                                    @RequestBody AutoInvestRequest request) {
        return ApiResponse.ok("자동모으기 등록 성공", autoInvestService.register(userId, request));
    }

    /** 종합 조회 — 전역 스위치 + 종목 목록(따로따로 한눈에). */
    @GetMapping
    public ApiResponse<AutoInvestOverviewResponse> overview(@CurrentUserId Long userId) {
        return ApiResponse.ok("자동모으기 조회 성공", autoInvestService.getOverview(userId));
    }

    /** 단건 상세. */
    @GetMapping("/{id}")
    public ApiResponse<AutoInvestResponse> detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.ok("자동모으기 상세 조회 성공", autoInvestService.getOne(userId, id));
    }

    /** 종목별 모으기 내역(회차별 체결/실패). */
    @GetMapping("/{id}/executions")
    public ApiResponse<List<AutoInvestExecutionResponse>> executions(@CurrentUserId Long userId,
                                                                     @PathVariable Long id) {
        return ApiResponse.ok("모으기 내역 조회 성공", autoInvestService.getExecutions(userId, id));
    }

    /** 설정 수정(주기·금액). */
    @PutMapping("/{id}")
    public ApiResponse<AutoInvestResponse> update(@CurrentUserId Long userId, @PathVariable Long id,
                                                  @RequestBody AutoInvestRequest request) {
        return ApiResponse.ok("자동모으기 수정 성공", autoInvestService.update(userId, id, request));
    }

    /** 일시중지/재개 — action=PAUSE/RESUME. */
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> status(@CurrentUserId Long userId, @PathVariable Long id,
                                    @RequestBody AutoInvestStatusRequest request) {
        autoInvestService.updateStatus(userId, id, request.action());
        return ApiResponse.ok("자동모으기 상태 변경 성공", null);
    }

    /** 해제(완전 삭제). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@CurrentUserId Long userId, @PathVariable Long id) {
        autoInvestService.remove(userId, id);
        return ApiResponse.ok("자동모으기 해제 성공", null);
    }
}
