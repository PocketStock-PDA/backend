package com.pocketstock.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러코드. 서비스별 세부 코드는 각 서비스에서 확장 가능.
 */
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TXN_AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "거래 인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "중복 또는 충돌이 발생했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 도메인 공통(예시)
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "잔액이 부족합니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "이미 체결·종결된 주문은 취소할 수 없습니다."),

    // 자산연동 - 계좌 1원 인증
    ACCOUNT_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증된 계좌입니다."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증 요청이 없거나 만료되었습니다. 다시 요청해 주세요."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다. 다시 요청해 주세요."),

    // 외부 연동(LS 등) 호출 실패 — 업스트림 장애
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서버 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
