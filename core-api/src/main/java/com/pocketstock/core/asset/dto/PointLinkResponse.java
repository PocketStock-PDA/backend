package com.pocketstock.core.asset.dto;

/** 포인트 개별 연동 응답 — 연동된 포인트사·잔액(1P=1원). */
public record PointLinkResponse(String companyCode, long balance) {
}
