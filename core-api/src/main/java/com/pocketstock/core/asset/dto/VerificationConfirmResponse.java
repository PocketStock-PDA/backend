package com.pocketstock.core.asset.dto;

/** 1원 인증 확인 결과. */
public record VerificationConfirmResponse(
        Long accountId,
        boolean verified
) {}
