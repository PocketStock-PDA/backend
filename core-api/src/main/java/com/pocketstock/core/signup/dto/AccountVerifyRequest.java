package com.pocketstock.core.signup.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 가입단계 계좌 1원 인증 송금요청. {@code accountId}는 가입 중(연동계좌·DB 없음) 화면이 넘기는
 * mock 식별자로, 서버는 검증하지 않고 echo만 한다(순수 시뮬레이션).
 */
public record AccountVerifyRequest(
        @NotNull(message = "계좌를 선택해 주세요.")
        Long accountId
) {}
