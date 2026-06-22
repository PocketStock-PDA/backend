package com.pocketstock.core.asset.dto;

import jakarta.validation.constraints.NotBlank;

/** 개별 연동 요청(은행·카드·포인트·증권 공용) — 연동할 기관 companyCode. */
public record InstitutionLinkRequest(
        @NotBlank(message = "연동할 기관을 선택해 주세요.")
        String companyCode
) {
}
