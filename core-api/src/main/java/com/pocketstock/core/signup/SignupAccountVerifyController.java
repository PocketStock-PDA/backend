package com.pocketstock.core.signup;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.signup.dto.AccountVerifyConfirmRequest;
import com.pocketstock.core.signup.dto.AccountVerifyConfirmResponse;
import com.pocketstock.core.signup.dto.AccountVerifyRequest;
import com.pocketstock.core.signup.dto.AccountVerifyRequestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입단계 계좌 1원 인증(소유권 확인) — 공개 API(로그인 전).
 *
 * <p>{@code @CurrentUserId}를 받지 않으므로 인증 없이 호출된다. 목데이터 영역의 순수 시뮬레이션이며,
 * 로그인 후 연동계좌 재인증({@code /api/assets/bank-accounts/{id}/verification})과는 별개 흐름이다.
 */
@RestController
@RequestMapping("/api/auth/account-verify")
@RequiredArgsConstructor
public class SignupAccountVerifyController {

    private final SignupAccountVerifyService service;

    /** 1원 인증 송금요청 — 입금자명·코드 생성(mock 응답에 노출). */
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<AccountVerifyRequestResponse>> request(
            @Valid @RequestBody AccountVerifyRequest request) {

        AccountVerifyRequestResponse data = service.request(request.accountId());
        return ResponseEntity.ok(ApiResponse.ok("1원을 입금했습니다. 입금자명을 확인해 주세요.", data));
    }

    /** 1원 인증 확인 — 입금자명 끝 3자리 대조. */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<AccountVerifyConfirmResponse>> confirm(
            @Valid @RequestBody AccountVerifyConfirmRequest request) {

        AccountVerifyConfirmResponse data = service.confirm(request.verificationId(), request.code());
        return ResponseEntity.ok(ApiResponse.ok("계좌 인증 확인", data));
    }
}
