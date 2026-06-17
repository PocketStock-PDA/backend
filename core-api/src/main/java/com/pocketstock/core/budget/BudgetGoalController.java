package com.pocketstock.core.budget;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.budget.dto.AutoBudgetGoalResponse;
import com.pocketstock.core.budget.dto.BudgetGoalRequest;
import com.pocketstock.core.budget.dto.BudgetGoalSummary;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetGoalController {

    private final BudgetGoalService budgetGoalService;

    @GetMapping("/goals")
    public ResponseEntity<ApiResponse<BudgetGoalSummary>> getGoals(
            @CurrentUserId Long userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        BudgetGoalSummary data = budgetGoalService.getGoals(userId);
        return ResponseEntity.ok(ApiResponse.ok("가계부 목표 조회 성공", data));
    }

    @PostMapping("/goals/auto")
    public ResponseEntity<ApiResponse<AutoBudgetGoalResponse>> setAutoGoals(
            @CurrentUserId Long userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AutoBudgetGoalResponse data = budgetGoalService.setAutoGoals(userId);
        return ResponseEntity.ok(ApiResponse.ok("소비분석 기반 목표 자동설정 성공", data));
    }

    @PostMapping("/goals")
    public ResponseEntity<ApiResponse<AutoBudgetGoalResponse>> setManualGoals(
            @CurrentUserId Long userId,
            @RequestBody BudgetGoalRequest request) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AutoBudgetGoalResponse data = budgetGoalService.setManualGoals(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("가계부 목표 설정 성공", data));
    }
}
