package com.pocketstock.core.asset.dto;

/** 일괄 연동 응답 — 이번 호출에서 새로 연동된 기관 수(이미 연동된 기관은 멱등 skip으로 제외). */
public record LinkResponse(int linkedCount) {
}
