package com.pocketstock.core.recommendations.maturity;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.recommendations.maturity.dto.MaturityRecommendationResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class MaturityRecommendationController {

    private final MaturityRecommendationService maturityRecommendationService;

    @GetMapping("/maturity")
    public ResponseEntity<ApiResponse<MaturityRecommendationResponse>> getMaturityRecommendations(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        MaturityRecommendationResponse data = maturityRecommendationService.recommend(userId);
        return ResponseEntity.ok(ApiResponse.ok("만기 도래 배당주 추천 조회 성공", data));
    }
}
