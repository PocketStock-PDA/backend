package com.pocketstock.core.asset.dto;

/** linked_institutions 조회 결과(멱등 판정용) — 커넥션 id + 연동상태. */
public record LinkedRef(Long id, String linkStatus) {
}
