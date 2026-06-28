package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssetSummaryResponse(
        BigDecimal netAssets,
        BigDecimal momDiff,
        String peerAgeGroup,
        int peerRankPercent,
        List<AssetPortfolioItem> portfolio,
        BigDecimal fixedExpenses,
        BigDecimal variableExpenses,
        /** 연동 포인트 잔액 합계(1P=1원) — '기타'에 포함. */
        BigDecimal points,
        /** 연동 포인트 출처별 내역 — '기타' 드릴다운에서 입출금과 분리 표기. */
        List<PointSource> pointSources,
        /** CMA·신투 등 일부 평가를 불러오지 못함 — 증권·순자산이 과소계상됐을 수 있음. */
        boolean partial
) {}
