package com.pocketstock.core.asset.dto;

/** 타 증권사 개별 연동 응답 — 연동된 보유 종목 수. */
public record SecuritiesLinkResponse(boolean linked, int holdingsCount) {
}
