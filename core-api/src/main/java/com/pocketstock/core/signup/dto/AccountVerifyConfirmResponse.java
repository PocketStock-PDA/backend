package com.pocketstock.core.signup.dto;

/** 가입단계 1원 인증 확인 응답. */
public record AccountVerifyConfirmResponse(
        boolean verified
) {}
