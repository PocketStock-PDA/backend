package com.pocketstock.core.asset.dto;

/** institution_master 조회 결과(연동 처리용) — 마스터 id + 카테고리. */
public record MasterRef(Long id, String category) {
}
