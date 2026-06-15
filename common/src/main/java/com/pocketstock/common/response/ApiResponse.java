package com.pocketstock.common.response;

/**
 * 모든 서비스 공통 응답 포맷.
 * 성공: { success: true, code: "SUCCESS", message: "...", data: ... }
 * 실패: { success: false, code: "ERROR_CODE", message: "...", data: null }
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, "SUCCESS", null, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
