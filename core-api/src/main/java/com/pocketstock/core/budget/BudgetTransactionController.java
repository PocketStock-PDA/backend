package com.pocketstock.core.budget;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.budget.dto.TransactionsResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetTransactionController {

    private final BudgetTransactionService budgetTransactionService;

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<TransactionsResponse>> getTransactions(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        TransactionsResponse data = budgetTransactionService.getTransactions(userId, type, year, month, day);
        return ResponseEntity.ok(ApiResponse.ok("소비내역 조회 성공", data));
    }
}
