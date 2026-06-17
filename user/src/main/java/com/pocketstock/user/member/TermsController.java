package com.pocketstock.user.member;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.member.dto.TermsAgreeRequest;
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
public class TermsController {

    private final TermsService termsService;

    /** 약관 동의 등록 — 인증 필요. */
    @PostMapping("/terms")
    public ResponseEntity<ApiResponse<Void>> agreeTerms(
            @CurrentUserId Long userId, @RequestBody TermsAgreeRequest request) {
        termsService.agree(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("약관 동의 등록 성공", null));
    }
}
