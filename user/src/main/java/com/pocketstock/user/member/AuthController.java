package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.member.dto.AuthTokens;
import com.pocketstock.user.member.dto.LoginRequest;
import com.pocketstock.user.member.dto.LoginResponse;
import com.pocketstock.user.member.dto.PinLoginRequest;
import com.pocketstock.user.member.dto.RefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final RefreshCookieFactory refreshCookieFactory;

    /** ID/PW 로그인 (JWT 발급) — 공개 API. X-Device-Id가 있으면 기기를 등록(PIN 로그인 기반). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        AuthTokens tokens = authService.login(request, deviceId);
        return loginResponse("로그인 성공", tokens);
    }

    /** PIN/패턴 간편 로그인 — 공개 API. 사용자 식별은 X-Device-Id 헤더로 한다. */
    @PostMapping("/login/pin")
    public ResponseEntity<ApiResponse<LoginResponse>> loginPin(
            @RequestBody PinLoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        AuthTokens tokens = authService.loginPin(request, deviceId);
        return loginResponse("간편 로그인 성공", tokens);
    }

    /**
     * 토큰 재발급 — 공개 API(만료된 accessToken으로도 호출 가능해야 하므로).
     * refresh token은 바디가 아니라 HttpOnly 쿠키에서 읽는다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            // 쿠키가 없거나 비었음 → 재로그인 필요
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token 쿠키가 없습니다.");
        }
        RefreshResponse data = authService.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("토큰 재발급 성공", data));
    }

    /** 로그아웃 — Redis의 refresh token을 폐기하고, 브라우저의 refresh 쿠키도 만료시킨다. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);   // null/blank도 멱등 처리
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.expired().toString())
                .body(ApiResponse.ok("로그아웃 성공", null));
    }

    /** 로그인 성공 응답 — accessToken·expiresIn은 바디, refreshToken은 Set-Cookie로 분배. */
    private ResponseEntity<ApiResponse<LoginResponse>> loginResponse(String message, AuthTokens tokens) {
        ResponseCookie cookie = refreshCookieFactory.create(tokens.refreshToken());
        LoginResponse body = new LoginResponse(tokens.accessToken(), tokens.expiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(message, body));
    }
}
