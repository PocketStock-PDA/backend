package com.pocketstock.user.member;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.member.dto.SetAuthMethodRequest;
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
public class AuthMethodController {

    private final AuthMethodService authMethodService;

    /** PIN/패턴 설정 — 인증 필요. */
    @PostMapping("/auth-method")
    public ResponseEntity<ApiResponse<Void>> setAuthMethod(
            @CurrentUserId Long userId, @RequestBody SetAuthMethodRequest request) {
        authMethodService.set(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("PIN/패턴 설정 성공", null));
    }
}
