package com.pocketstock.core.budget;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.budget.dto.CategorySavingsResponse;
import com.pocketstock.core.budget.dto.ComparisonResponse;
import com.pocketstock.core.budget.dto.SavingsStatusResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetSavingsController {

    private final BudgetSavingsService budgetSavingsService;

    @GetMapping("/savings/by-category")
    public ResponseEntity<ApiResponse<CategorySavingsResponse>> getSavingsByCategory(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.ok("카테고리별 절약 현황 조회 성공",
                budgetSavingsService.getCategoryWithSavings(userId)));
    }

    @GetMapping("/comparison")
    public ResponseEntity<ApiResponse<ComparisonResponse>> getComparison(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.ok("소비 섹터별 전월 비교 조회 성공",
                budgetSavingsService.getComparison(userId)));
    }

    @GetMapping("/savings")
    public ResponseEntity<ApiResponse<SavingsStatusResponse>> getSavingsStatus(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.ok("절약금 현황 조회 성공",
                budgetSavingsService.getSavingsStatus(userId)));
    }

    @PostMapping("/savings/agree")
    public ResponseEntity<ApiResponse<Void>> agreeCollect(
            @CurrentUserId Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        budgetSavingsService.agreeCollect(userId);
        return ResponseEntity.ok(ApiResponse.ok("절약금 모으기 동의 완료", null));
    }
}
