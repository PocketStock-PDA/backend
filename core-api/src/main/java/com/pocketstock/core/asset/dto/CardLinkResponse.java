package com.pocketstock.core.asset.dto;

/** 카드 개별 연동 응답 — 연동된 카드 수. */
public record CardLinkResponse(boolean linked, int cardCount) {
}
