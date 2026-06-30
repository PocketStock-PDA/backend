package com.pocketstock.core.recommendations.deposit;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.recommendations.deposit.dto.CanceledRerouteRequest;
import com.pocketstock.core.recommendations.deposit.dto.DepositProductDto;
import com.pocketstock.core.recommendations.deposit.dto.DepositRolloverDto;
import com.pocketstock.core.recommendations.deposit.dto.DepositRolloverRequest;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 만기 굴리기 '예금 재예치' — 예적금 상품 추천 목록 + 재예치 생성/조회.
 * 배당주 추천(MaturityRecommendationController)과 짝을 이룬다.
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;

    /** 예적금 상품 추천 목록(최고금리 순). */
    @GetMapping("/deposits")
    public ResponseEntity<ApiResponse<List<DepositProductDto>>> getDepositProducts(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.ok("예적금 상품 목록 조회 성공", depositService.listProducts()));
    }

    /** '예금 재예치' 생성 — 만기 자금 일부를 선택한 예적금 상품으로 재예치 예약. */
    @PostMapping("/maturity/deposit-rollover")
    public ResponseEntity<ApiResponse<Void>> createDepositRollover(
            @CurrentUserId Long userId,
            @RequestBody DepositRolloverRequest request) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        depositService.createRollover(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("예금 재예치 예약 성공", null));
    }

    /** '재예치 없이 CMA로 이체' — 만기 자금 잔여분을 CMA 원화풀로 옮긴다. */
    @PostMapping("/maturity/deposit-to-cma")
    public ResponseEntity<ApiResponse<Void>> transferToCma(
            @CurrentUserId Long userId,
            @RequestBody DepositRolloverRequest request) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        depositService.transferToCma(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("CMA 이체 성공", null));
    }

    /** 배당주 예약 취소분 라우팅 — 활성 rollover 있으면 합산, 없으면 target(CMA/재예치)으로 새로 생성. */
    @PostMapping("/maturity/canceled-reroute")
    public ResponseEntity<ApiResponse<Void>> rerouteCanceled(
            @CurrentUserId Long userId,
            @RequestBody CanceledRerouteRequest request) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        depositService.rerouteCanceled(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("취소분 라우팅 완료", null));
    }

    /** rollover 예약 취소 — RESERVED 상태인 경우만 가능(CMA 이체·예금 재예치 공용). */
    @DeleteMapping("/maturity/rollover/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelRollover(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        depositService.cancelRollover(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("전환 예약이 취소됐어요.", null));
    }

    /** '예금 재예치' 기록 — 전환내역에서 배당주 예약과 병합. */
    @GetMapping("/maturity/deposit-rollovers")
    public ResponseEntity<ApiResponse<List<DepositRolloverDto>>> getDepositRollovers(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.ok("예금 재예치 기록 조회 성공", depositService.listRollovers(userId)));
    }
}
