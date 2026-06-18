package com.pocketstock.user.member.dto;

import java.time.LocalDateTime;

/** 거래 인증 응답. 인증 시각과 30분 후 만료 시각. */
public record VerifyAccountPasswordResponse(LocalDateTime verifiedAt, LocalDateTime expiresAt) {
}
