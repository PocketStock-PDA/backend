package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.asset.dto.VerificationConfirmRequest;
import com.pocketstock.core.asset.dto.VerificationConfirmResponse;
import com.pocketstock.core.asset.dto.VerificationRequestResponse;
import com.pocketstock.user.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계좌 1원 인증(소유권 확인) — <b>로그인 후</b> 연동 은행 계좌용. 송금요청 → 푸시(알림함) 코드 입력 → 확인.
 * 인증(@CurrentUserId) 필요, 연동 은행 계좌(linked_bank_accounts) 대상, 성공 시 {@code is_verified} 마킹(목데이터 영역).
 *
 * <p><b>가입단계(로그인 전) 공개 mock 인증과는 별개 흐름</b>이다 →
 * {@code com.pocketstock.core.signup.SignupAccountVerifyController}({@code POST /api/auth/account-verify/*}).
 * 그쪽은 userId·DB 무결합 휘발 mock(응답에 코드 노출), 이쪽은 로그인 후 영속 마킹·푸시 전달.
 */
@RestController
@RequestMapping("/api/assets/bank-accounts")
@RequiredArgsConstructor
public class BankVerificationController {

    private final BankVerificationService verificationService;

    /** 1원 인증 송금요청 — 코드 생성 후 목 1원 입금 푸시 발송. */
    @PostMapping("/{accountId}/verification")
    public ResponseEntity<ApiResponse<VerificationRequestResponse>> request(
            @CurrentUserId Long userId,
            @PathVariable Long accountId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        VerificationRequestResponse data = verificationService.request(userId, accountId);
        return ResponseEntity.ok(ApiResponse.ok("인증 코드를 발송했습니다.", data));
    }

    /** 1원 인증 확인 — 푸시로 받은 코드 검증. */
    @PostMapping("/{accountId}/verification/confirm")
    public ResponseEntity<ApiResponse<VerificationConfirmResponse>> confirm(
            @CurrentUserId Long userId,
            @PathVariable Long accountId,
            @Valid @RequestBody VerificationConfirmRequest request) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        VerificationConfirmResponse data = verificationService.confirm(userId, accountId, request.code());
        return ResponseEntity.ok(ApiResponse.ok("계좌 인증이 완료되었습니다.", data));
    }
}
