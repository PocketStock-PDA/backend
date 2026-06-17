package com.pocketstock.user.member;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.member.dto.LoginRequest;
import com.pocketstock.user.member.dto.LoginResponse;
import com.pocketstock.user.member.dto.LogoutRequest;
import com.pocketstock.user.member.dto.RefreshRequest;
import com.pocketstock.user.member.dto.RefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** ID/PW 로그인 (JWT 발급) — 공개 API. */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        LoginResponse data = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("로그인 성공", data));
    }

    /** 토큰 재발급 — 공개 API(만료된 accessToken으로도 호출 가능해야 하므로). */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@RequestBody RefreshRequest request) {
        RefreshResponse data = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok("토큰 재발급 성공", data));
    }

    /** 로그아웃 — refreshToken 폐기. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 성공", null));
    }
}
