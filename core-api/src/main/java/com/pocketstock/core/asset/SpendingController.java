package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.asset.dto.SpendingResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class SpendingController {

    private final SpendingService spendingService;

    @GetMapping("/spending")
    public ResponseEntity<ApiResponse<SpendingResponse>> getSpending(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SpendingResponse data = spendingService.getSpending(userId, year, month);
        return ResponseEntity.ok(ApiResponse.ok("소비패턴 분석 결과 조회 성공", data));
    }
}
