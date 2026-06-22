package com.pocketstock.core.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 선택 기관 일괄 연동 요청. {@code authToken}은 무상태 mock이라 검증하지 않는다(없어도 무방).
 * {@code institutions}는 연동할 기관 companyCode 목록.
 */
public record LinkRequest(
        String authToken,
        @NotEmpty(message = "연동할 기관을 선택해 주세요.")
        List<@NotBlank(message = "기관 코드가 비어 있습니다.") String> institutions
) {
}
