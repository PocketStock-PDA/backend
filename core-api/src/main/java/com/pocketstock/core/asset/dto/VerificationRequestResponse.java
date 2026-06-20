package com.pocketstock.core.asset.dto;

/**
 * 1원 인증 송금요청 응답. 코드는 포함하지 않는다(푸시로만 전달).
 * 클라이언트는 만료 타이머·시도 한도 안내에 사용한다.
 */
public record VerificationRequestResponse(
        Long accountId,
        String senderName,        // 목 1원 입금자명(은행 거래내역 표기 시뮬)
        long expiresInSeconds,
        int maxAttempts
) {}
