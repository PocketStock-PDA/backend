package com.pocketstock.core.asset.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 마이데이터 통합인증 요청 — 인증할 기관 companyCode 목록. */
public record LinkAuthRequest(
        @NotEmpty(message = "인증할 기관을 선택해 주세요.")
        List<String> institutions
) {
}
