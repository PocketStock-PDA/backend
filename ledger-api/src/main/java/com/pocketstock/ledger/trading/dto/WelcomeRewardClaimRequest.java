package com.pocketstock.ledger.trading.dto;

/**
 * 웰컴 보상 지급 요청 — 후보 4종목 중 사용자가 고른 1종목.
 * market은 종목마스터 통화에서 도출하므로 종목코드만 받는다.
 */
public record WelcomeRewardClaimRequest(String stockCode) {
}
