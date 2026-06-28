package com.pocketstock.core.recommendations.maturity;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.recommendations.maturity.dto.MaturityRecommendationResponse;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountDto;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class MaturityRecommendationController {

    private final MaturityRecommendationService maturityRecommendationService;

    /** 만기 굴리기 대상 예적금 목록(미래 만기·임박 순) — 선택 화면용. */
    @GetMapping("/maturity/accounts")
    public ResponseEntity<ApiResponse<List<TriggerAccountDto>>> getMaturityAccounts(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        List<TriggerAccountDto> data = maturityRecommendationService.listAccounts(userId);
        return ResponseEntity.ok(ApiResponse.ok("만기 굴리기 대상 예적금 목록 조회 성공", data));
    }

    /** 배당주 추천 — accountId 지정 시 해당 예적금 기준, 없으면 가장 가까운 만기 자동 선택. */
    @GetMapping("/maturity")
    public ResponseEntity<ApiResponse<MaturityRecommendationResponse>> getMaturityRecommendations(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long accountId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        MaturityRecommendationResponse data = maturityRecommendationService.recommend(userId, accountId);
        return ResponseEntity.ok(ApiResponse.ok("만기 도래 배당주 추천 조회 성공", data));
    }
}
