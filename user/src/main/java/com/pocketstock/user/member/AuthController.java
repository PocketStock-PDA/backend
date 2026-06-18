package com.pocketstock.user.member;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.member.dto.LoginRequest;
import com.pocketstock.user.member.dto.LoginResponse;
import com.pocketstock.user.member.dto.LogoutRequest;
import com.pocketstock.user.member.dto.PinLoginRequest;
import com.pocketstock.user.member.dto.RefreshRequest;
import com.pocketstock.user.member.dto.RefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** ID/PW 로그인 (JWT 발급) — 공개 API. X-Device-Id가 있으면 기기를 등록(PIN 로그인 기반). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse data = authService.login(request, deviceId);
        return ResponseEntity.ok(ApiResponse.ok("로그인 성공", data));
    }

    /** PIN/패턴 간편 로그인 — 공개 API. 사용자 식별은 X-Device-Id 헤더로 한다. */
    @PostMapping("/login/pin")
    public ResponseEntity<ApiResponse<LoginResponse>> loginPin(
            @RequestBody PinLoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse data = authService.loginPin(request, deviceId);
        return ResponseEntity.ok(ApiResponse.ok("간편 로그인 성공", data));
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
