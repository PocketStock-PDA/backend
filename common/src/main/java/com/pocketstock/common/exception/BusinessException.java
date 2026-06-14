package com.pocketstock.common.exception;

import lombok.Getter;

/**
 * 비즈니스 예외. 서비스 로직에서 throw new BusinessException(ErrorCode.XXX) 형태로 사용.
 * GlobalExceptionHandler가 잡아 ErrorCode의 status·message로 응답.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
