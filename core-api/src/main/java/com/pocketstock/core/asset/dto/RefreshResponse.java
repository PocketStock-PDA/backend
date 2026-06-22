package com.pocketstock.core.asset.dto;

import java.time.LocalDateTime;

/** 연동 자산 새로고침 응답 — 갱신된 동기화 시각(no-op). */
public record RefreshResponse(LocalDateTime syncedAt) {
}
