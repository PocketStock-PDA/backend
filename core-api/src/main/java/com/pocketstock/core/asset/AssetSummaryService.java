package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.AssetCategoryRow;
import com.pocketstock.core.asset.dto.AssetPortfolioItem;
import com.pocketstock.core.asset.dto.AssetSummaryResponse;
import com.pocketstock.core.asset.dto.PointSource;
import com.pocketstock.core.asset.mapper.AssetSummaryMapper;
import com.pocketstock.core.client.LedgerFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSummaryService {

    private static final String CATEGORY_SECURITIES = "증권";
    private static final String CATEGORY_ETC = "기타";
    private static final String TYPE_FIXED = "FIXED";
    private static final String TYPE_VARIABLE = "VARIABLE";

    private final AssetSummaryMapper mapper;
    private final LedgerFeignClient ledgerFeignClient;

    @Transactional(readOnly = true)
    public AssetSummaryResponse getSummary(Long userId) {
        // 은행 계좌 카테고리별 집계 + 연동 포인트(1P=1원)를 '기타'에 합산
        List<AssetCategoryRow> bankRows = new ArrayList<>(mapper.findBankAssetsByCategory(userId));
        List<PointSource> pointSources = mapper.findPoints(userId);
        BigDecimal points = sumPoints(pointSources);
        mergePointsIntoEtc(bankRows, points);

        // 증권 카테고리 = 타사 외부보유 + CMA 총평가(KRW) + 신투(자체 증권계좌) 보유 평가(KRW)
        BigDecimal securitiesAmount = mapper.sumExternalHoldings(userId)
                .add(fetchCmaKrwTotal(userId))
                .add(fetchOwnHoldingsKrw(userId));

        // 이번 달 범위
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
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
                variableExpenses,
                points,            // '기타'에 포함된 포인트 합계
                pointSources       // 포인트 출처별 내역(드릴다운 표기용)
        );
    }

    /** 포인트 출처 목록의 잔액 합계. null 잔액은 0으로 처리. */
    private BigDecimal sumPoints(List<PointSource> pointSources) {
        return pointSources.stream()
                .map(PointSource::getBalance)
                .filter(b -> b != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 연동 포인트를 '기타' 카테고리에 합산(없으면 행 추가). 이후 bankTotal·순자산에 자동 반영. */
    private void mergePointsIntoEtc(List<AssetCategoryRow> bankRows, BigDecimal points) {
        if (points.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AssetCategoryRow etc = bankRows.stream()
                .filter(r -> CATEGORY_ETC.equals(r.getCategory()))
                .findFirst()
                .orElse(null);
        if (etc != null) {
            etc.setAmount(etc.getAmount().add(points));
        } else {
            AssetCategoryRow row = new AssetCategoryRow();
            row.setCategory(CATEGORY_ETC);
            row.setAmount(points);
            bankRows.add(row);
        }
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

    private BigDecimal fetchCmaKrwTotal(Long userId) {
        try {
            BigDecimal total = ledgerFeignClient.getCmaTotalKrw(userId);
            return total != null ? total : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("CMA 잔액 조회 실패 (userId={}): {}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** 신투(자체 증권계좌) 보유 평가액(KRW 환산) — 보유 × 현재가, 해외는 환율 환산. 보유 없거나 실패 시 0. */
    private BigDecimal fetchOwnHoldingsKrw(Long userId) {
        try {
            BigDecimal total = ledgerFeignClient.getPuzzleValuation(userId);
            return total != null ? total : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("신투 보유 평가액 조회 실패 (userId={}): {}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal ratio(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }
}
