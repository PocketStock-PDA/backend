package com.pocketstock.user.auth;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.user.auth.dto.CertRequest;
import com.pocketstock.user.auth.dto.CertResponse;
import com.pocketstock.user.auth.dto.CertVerifyRequest;
import com.pocketstock.user.auth.dto.SmsSendRequest;
import com.pocketstock.user.auth.dto.SmsSendResponse;
import com.pocketstock.user.auth.dto.SmsVerifyRequest;
import com.pocketstock.user.auth.dto.VerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴대폰 본인확인(mock) — 난수문자 인증요청/대조 + SMS 인증번호 발송/확인. 모두 공개 API.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    /** 난수문자 인증요청 — randomCode 발급(프론트 문자창 표시용). */
    @PostMapping("/shinhan-cert/request")
    public ResponseEntity<ApiResponse<CertResponse>> requestCert(@RequestBody CertRequest request) {
        CertResponse data = phoneVerificationService.requestCert(request);
        return ResponseEntity.ok(ApiResponse.ok("난수문자 인증요청 성공", data));
    }

    /** 난수문자 대조 확인 — echo로 받은 randomCode 일치 검증. */
    @PostMapping("/shinhan-cert/verify")
    public ResponseEntity<ApiResponse<VerifyResponse>> verifyCert(@RequestBody CertVerifyRequest request) {
        VerifyResponse data = phoneVerificationService.verifyCert(request);
        return ResponseEntity.ok(ApiResponse.ok("난수문자 대조 확인 성공", data));
    }

    /** SMS 인증번호 발송(mock — 실제 발송 없이 코드를 응답으로 반환, 프론트가 도착 연출). */
    @PostMapping("/sms/send")
    public ResponseEntity<ApiResponse<SmsSendResponse>> sendSms(@RequestBody SmsSendRequest request) {
        SmsSendResponse data = phoneVerificationService.sendSms(request);
        return ResponseEntity.ok(ApiResponse.ok("SMS 인증번호 발송 성공", data));
    }

    /** SMS 인증번호 확인 — 발급 코드와 대조. */
    @PostMapping("/sms/verify")
    public ResponseEntity<ApiResponse<VerifyResponse>> verifySms(@RequestBody SmsVerifyRequest request) {
        VerifyResponse data = phoneVerificationService.verifySms(request);
        return ResponseEntity.ok(ApiResponse.ok("SMS 인증 확인 성공", data));
    }
}
