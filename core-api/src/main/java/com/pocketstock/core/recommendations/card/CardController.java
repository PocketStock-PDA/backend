package com.pocketstock.core.recommendations.card;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.recommendations.card.dto.CardRecommendationItem;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class CardController {

    private final CardRecommendationService cardRecommendationService;

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<List<CardRecommendationItem>>> getRecommendations(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        List<CardRecommendationItem> data = cardRecommendationService.recommend(userId);
        return ResponseEntity.ok(ApiResponse.ok("맞춤 카드 추천 조회 성공", data));
    }
}
