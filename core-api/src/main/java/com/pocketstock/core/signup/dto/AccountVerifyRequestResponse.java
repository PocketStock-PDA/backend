package com.pocketstock.core.signup.dto;

/**
 * 가입단계 1원 인증 송금요청 응답.
 *
 * <p>실제 송금이 없는 mock이라 검증 코드를 전달할 out-of-band 채널(로그인 전이라 알림함·푸시 불가)이 없다.
 * 따라서 {@code depositorName}(데모 화면용)과 {@code code}(테스트 보조)를 응답에 그대로 노출한다.
 * 운영(실 펌뱅킹) 전환 시 두 필드는 응답에서 제거해야 한다.
 */
public record AccountVerifyRequestResponse(
        String verificationId,
        String depositorName,   // 예: 포켓스톡482 — 화면에 "이 입금자명으로 1원 입금" 시뮬
        String code,            // 입금자명 끝 3자리(테스트/디버그 보조)
        long expiresIn          // 초
) {}
