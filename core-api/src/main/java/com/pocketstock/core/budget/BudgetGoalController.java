package com.pocketstock.core.budget;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.budget.dto.AutoBudgetGoalResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetGoalController {

    private final BudgetGoalService budgetGoalService;

    @PostMapping("/goals/auto")
    public ResponseEntity<ApiResponse<AutoBudgetGoalResponse>> setAutoGoals(
            @CurrentUserId Long userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AutoBudgetGoalResponse data = budgetGoalService.setAutoGoals(userId);
        return ResponseEntity.ok(ApiResponse.ok("소비분석 기반 목표 자동설정 성공", data));
    }
}
