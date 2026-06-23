package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.asset.dto.AssetSummaryResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetSummaryController {

    private final AssetSummaryService assetSummaryService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AssetSummaryResponse>> getSummary(
            @CurrentUserId Long userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AssetSummaryResponse data = assetSummaryService.getSummary(userId);
        return ResponseEntity.ok(ApiResponse.ok("자산 분석 요약 조회 성공", data));
    }
}
