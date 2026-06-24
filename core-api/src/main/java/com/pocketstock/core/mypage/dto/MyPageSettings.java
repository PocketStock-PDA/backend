package com.pocketstock.core.mypage.dto;

/**
 * 마이페이지 자동 모으기 토글 상태.
 * - cardChangeCollect: ledger collection_settings(sourceType=CARD)의 마스터 토글
 * - monthlySavingCollect: core budget_savings(현재월) is_collect_agreed
 */
public record MyPageSettings(
        boolean cardChangeCollect,
        boolean monthlySavingCollect
) {}
