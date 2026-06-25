package com.pocketstock.user.member;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.member.dto.SetAccountPasswordRequest;
import com.pocketstock.user.member.dto.UnlockAccountPasswordRequest;
import com.pocketstock.user.member.dto.VerifyAccountPasswordRequest;
import com.pocketstock.user.member.dto.VerifyAccountPasswordResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AccountPasswordController {

    private final AccountPasswordService accountPasswordService;

    /** 계좌 비밀번호 설정 — 인증 필요. */
    @PostMapping("/account-password")
    public ResponseEntity<ApiResponse<Void>> setAccountPassword(
            @CurrentUserId Long userId, @RequestBody SetAccountPasswordRequest request) {
        accountPasswordService.set(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("계좌 비밀번호 설정 성공", null));
    }

    /** 거래 인증(계좌 비밀번호 검증, 30분 유지) — 인증 필요. */
    @PostMapping("/account-password/verify")
    public ResponseEntity<ApiResponse<VerifyAccountPasswordResponse>> verifyAccountPassword(
            @CurrentUserId Long userId, @RequestBody VerifyAccountPasswordRequest request) {
        VerifyAccountPasswordResponse data = accountPasswordService.verify(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("거래 인증 성공", data));
    }

    /** 잠금 해제 인증번호 발송 — 등록 휴대폰으로 푸시 전송(잠금 상태 + 푸시 구독 필요). */
    @PostMapping("/account-password/unlock/sms")
    public ResponseEntity<ApiResponse<Void>> sendUnlockCode(@CurrentUserId Long userId) {
        accountPasswordService.sendUnlockCode(userId);
        return ResponseEntity.ok(ApiResponse.ok("잠금 해제 인증번호 발송 성공", null));
    }

    /** 잠금 해제 — 푸시로 받은 인증번호 검증 후 잠금 해제. */
    @PostMapping("/account-password/unlock")
    public ResponseEntity<ApiResponse<Void>> unlock(
            @CurrentUserId Long userId, @RequestBody UnlockAccountPasswordRequest request) {
        accountPasswordService.unlock(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("계좌 비밀번호 잠금 해제 성공", null));
    }
}
