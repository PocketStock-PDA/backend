package com.pocketstock.core.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 계좌 1원 인증 확인 요청 — 푸시로 받은 코드. */
public record VerificationConfirmRequest(
        @NotBlank(message = "인증 코드를 입력해 주세요.")
        @Pattern(regexp = "\\d{3}", message = "인증 코드는 3자리 숫자입니다.")
        String code
) {}
