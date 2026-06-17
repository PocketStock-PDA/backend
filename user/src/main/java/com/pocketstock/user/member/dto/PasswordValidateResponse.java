package com.pocketstock.user.member.dto;

import java.util.List;

/**
 * 비밀번호 실시간 검증 응답.
 * valid=true면 failedRules는 빈 배열. 실패 규칙은 코드로 전달(MIN_LENGTH 등).
 */
public record PasswordValidateResponse(boolean valid, List<String> failedRules) {
}
