package com.pocketstock.user.member;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.member.dto.FindUsernameRequest;
import com.pocketstock.user.member.dto.FindUsernameResponse;
import com.pocketstock.user.member.dto.PasswordValidateRequest;
import com.pocketstock.user.member.dto.PasswordValidateResponse;
import com.pocketstock.user.member.dto.ResetPasswordRequest;
import com.pocketstock.user.member.dto.SignupRequest;
import com.pocketstock.user.member.dto.SignupResponse;
import com.pocketstock.user.member.dto.UpdatePasswordRequest;
import com.pocketstock.user.member.dto.UsernameCheckResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<UsernameCheckResponse>> checkUsername(
            @RequestParam String username) {
        UsernameCheckResponse data = memberService.checkUsername(username);
        return ResponseEntity.ok(ApiResponse.ok("아이디 사용 가능 여부 조회 성공", data));
    }

    /** 회원가입 — 가입 전 공개 API. */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@RequestBody SignupRequest request) {
        SignupResponse data = memberService.signup(request);
        return ResponseEntity.ok(ApiResponse.ok("회원가입 성공", data));
    }

    /** 비밀번호 보안규칙 실시간 검증 — 규칙 위반도 200으로 valid/failedRules 반환. */
    @PostMapping("/validate-password")
    public ResponseEntity<ApiResponse<PasswordValidateResponse>> validatePassword(
            @RequestBody PasswordValidateRequest request) {
        PasswordValidateResponse data = memberService.validatePassword(request.password());
        return ResponseEntity.ok(ApiResponse.ok("비밀번호 검증 성공", data));
    }

    /** 아이디 찾기 — 공개 API. 이름+휴대폰 본인확인 후 마스킹된 아이디 반환. */
    @PostMapping("/find-username")
    public ResponseEntity<ApiResponse<FindUsernameResponse>> findUsername(
            @RequestBody FindUsernameRequest request) {
        FindUsernameResponse data = memberService.findUsername(request);
        return ResponseEntity.ok(ApiResponse.ok("아이디 찾기 성공", data));
    }

    /** 비밀번호 찾기/재설정 — 공개 API. 아이디+휴대폰 본인확인 후 재설정. */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestBody ResetPasswordRequest request) {
        memberService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("비밀번호 재설정 성공", null));
    }

    /** 회원정보(비밀번호) 수정 — 인증 필요. */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateMe(
            @CurrentUserId Long userId, @RequestBody UpdatePasswordRequest request) {
        memberService.updatePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("회원정보 수정 성공", null));
    }
}
