package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.AssetCategoryRow;
import com.pocketstock.core.asset.dto.AssetPortfolioItem;
import com.pocketstock.core.asset.dto.AssetSummaryResponse;
import com.pocketstock.core.asset.mapper.AssetSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetSummaryService {

    private static final String CATEGORY_SECURITIES = "증권";
    private static final String TYPE_FIXED = "FIXED";
    private static final String TYPE_VARIABLE = "VARIABLE";

    private final AssetSummaryMapper mapper;

    @Transactional(readOnly = true)
    public AssetSummaryResponse getSummary(Long userId) {
        // 은행 계좌 카테고리별 집계
        List<AssetCategoryRow> bankRows = mapper.findBankAssetsByCategory(userId);

        // 타사 증권 평가금액
        BigDecimal securitiesAmount = mapper.sumExternalHoldings(userId);

        // 이번 달 범위
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        // 고정비/변동비 집계
        List<AssetCategoryRow> spendingRows = mapper.findSpendingByType(userId, from, to);
        Map<String, BigDecimal> spendingMap = spendingRows.stream()
                .collect(Collectors.toMap(
                        AssetCategoryRow::getCategory,
                        AssetCategoryRow::getAmount,
                        BigDecimal::add
                ));

        BigDecimal fixedExpenses    = spendingMap.getOrDefault(TYPE_FIXED, BigDecimal.ZERO);
        BigDecimal variableExpenses = spendingMap.getOrDefault(TYPE_VARIABLE, BigDecimal.ZERO);

        // 순자산 = 은행 계좌 합계 + 타사 증권
        BigDecimal bankTotal = bankRows.stream()
                .map(AssetCategoryRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netAssets = bankTotal.add(securitiesAmount);

        // 포트폴리오 목록 구성
        List<AssetPortfolioItem> portfolio = buildPortfolio(bankRows, securitiesAmount, netAssets);

        return new AssetSummaryResponse(
                netAssets,
                BigDecimal.ZERO,   // momDiff: 스냅샷 미구현, 0 반환
                "",                // peerAgeGroup: 인구통계 미구현
                0,                 // peerRankPercent: 인구통계 미구현
                portfolio,
                fixedExpenses,
                variableExpenses
        );
    }

    private List<AssetPortfolioItem> buildPortfolio(
            List<AssetCategoryRow> bankRows,
            BigDecimal securitiesAmount,
            BigDecimal netAssets
    ) {
        List<AssetPortfolioItem> items = new ArrayList<>();

        for (AssetCategoryRow row : bankRows) {
            items.add(new AssetPortfolioItem(
                    row.getCategory(),
                    row.getAmount(),
                    ratio(row.getAmount(), netAssets)
            ));
        }

        if (securitiesAmount.compareTo(BigDecimal.ZERO) > 0) {
            items.add(new AssetPortfolioItem(
                    CATEGORY_SECURITIES,
                    securitiesAmount,
                    ratio(securitiesAmount, netAssets)
            ));
            items.sort((a, b) -> b.amount().compareTo(a.amount()));
        }

        return items;
    }

    private BigDecimal ratio(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }
}
