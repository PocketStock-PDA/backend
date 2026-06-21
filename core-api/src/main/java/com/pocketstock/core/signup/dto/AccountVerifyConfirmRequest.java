package com.pocketstock.core.signup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 가입단계 1원 인증 확인 요청 — 입금자명 끝 3자리. */
public record AccountVerifyConfirmRequest(
        @NotBlank(message = "인증 요청 정보가 없습니다.")
        String verificationId,

        @NotBlank(message = "인증 코드를 입력해 주세요.")
        @Pattern(regexp = "\\d{3}", message = "인증 코드는 3자리 숫자입니다.")
        String code
) {}
