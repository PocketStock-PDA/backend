package com.pocketstock.user.auth;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.auth.dto.CertRequest;
import com.pocketstock.user.auth.dto.CertResponse;
import com.pocketstock.user.auth.dto.CertVerifyRequest;
import com.pocketstock.user.auth.dto.SmsSendRequest;
import com.pocketstock.user.auth.dto.SmsSendResponse;
import com.pocketstock.user.auth.dto.SmsVerifyRequest;
import com.pocketstock.user.auth.dto.VerifyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 휴대폰 본인확인 mock — 실제 SMS 발송/수신 없이 인메모리 echo 대조로 동작.
 * - 난수문자(MO 흉내): request로 randomCode 발급 → 프론트 "문자 보내기" 화면 표시 → verify로 echo 대조
 * - SMS 인증번호(MT mock): send로 코드 발급(로그 출력) → verify로 대조
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final Duration CERT_TTL = Duration.ofSeconds(180);
    private static final Duration SMS_TTL = Duration.ofSeconds(180);

    private static final SecureRandom RANDOM = new SecureRandom();
    // 혼동 문자(0/O, 1/I 등) 제외한 난수문자 알파벳
    private static final String CERT_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CERT_CODE_LENGTH = 6;
    private static final int SMS_CODE_LENGTH = 6;

    private final VerificationStore store;

    /** 난수문자 인증요청 — randomCode 발급 + 세션 저장(TTL). */
    public CertResponse requestCert(CertRequest req) {
        if (!StringUtils.hasText(req.username())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디는 필수입니다.");
        }
        String requestId = "REQ-" + UUID.randomUUID();
        String randomCode = randomCertCode();
        store.saveCert(requestId, randomCode, CERT_TTL);
        return new CertResponse(requestId, randomCode, (int) CERT_TTL.getSeconds());
    }

    /** 난수문자 대조 확인 — echo로 받은 randomCode를 세션 발급값과 비교. */
    public VerifyResponse verifyCert(CertVerifyRequest req) {
        if (!StringUtils.hasText(req.requestId()) || !StringUtils.hasText(req.randomCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "requestId와 randomCode는 필수입니다.");
        }
        return new VerifyResponse(store.verifyCert(req.requestId(), req.randomCode()));
    }

    /**
     * SMS 인증번호 발송(mock) — 코드 발급·보관 후 응답으로 반환(프론트가 "문자 도착" 연출).
     * 실제 발송은 하지 않으며, 운영 전환 시 응답의 code 노출은 제거한다.
     */
    public SmsSendResponse sendSms(SmsSendRequest req) {
        if (!StringUtils.hasText(req.phone())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "휴대폰 번호는 필수입니다.");
        }
        String code = randomNumericCode();
        store.saveSms(req.phone(), code, SMS_TTL);
        log.info("[SMS mock] {} 인증번호: {}", maskPhone(req.phone()), code); // mock: 실제 발송 없음
        return new SmsSendResponse(code, (int) SMS_TTL.getSeconds());
    }

    /** SMS 인증번호 확인 — 발급 코드와 대조. */
    public VerifyResponse verifySms(SmsVerifyRequest req) {
        if (!StringUtils.hasText(req.phone()) || !StringUtils.hasText(req.code())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "휴대폰 번호와 인증번호는 필수입니다.");
        }
        return new VerifyResponse(store.verifySms(req.phone(), req.code()));
    }

    private String randomCertCode() {
        StringBuilder sb = new StringBuilder(CERT_CODE_LENGTH);
        for (int i = 0; i < CERT_CODE_LENGTH; i++) {
            sb.append(CERT_ALPHABET.charAt(RANDOM.nextInt(CERT_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String randomNumericCode() {
        int bound = (int) Math.pow(10, SMS_CODE_LENGTH); // 6자리 → 1_000_000
        return String.format("%0" + SMS_CODE_LENGTH + "d", RANDOM.nextInt(bound));
    }

    private String maskPhone(String phone) {
        if (phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
