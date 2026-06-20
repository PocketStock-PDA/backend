package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.asset.dto.BankAccountResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    /** 유저 보유(연동) 은행 계좌 목록. 1원 인증·재원 계좌 선택 등 공용 조회. */
    @GetMapping("/bank-accounts")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> getBankAccounts(
            @CurrentUserId Long userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<BankAccountResponse> data = bankAccountService.getBankAccounts(userId);
        return ResponseEntity.ok(ApiResponse.ok("보유 은행 계좌 목록 조회 성공", data));
    }
}
