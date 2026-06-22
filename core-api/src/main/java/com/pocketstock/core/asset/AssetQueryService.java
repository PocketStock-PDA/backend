package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.DormantAccountResponse;
import com.pocketstock.core.asset.dto.ExternalHoldingResponse;
import com.pocketstock.core.asset.dto.ExternalHoldingRow;
import com.pocketstock.core.asset.dto.InstitutionResponse;
import com.pocketstock.core.asset.dto.ScanResponse;
import com.pocketstock.core.asset.mapper.ExternalHoldingMapper;
import com.pocketstock.core.asset.mapper.InstitutionMapper;
import com.pocketstock.core.asset.mapper.LinkedAssetMapper;
import com.pocketstock.core.client.LedgerFeignClient;
import com.pocketstock.core.client.dto.CollectionSettingView;
import com.pocketstock.core.internal.asset.InternalAssetService;
import com.pocketstock.core.internal.asset.dto.LinkedAccountSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 연동 자산 조회 전용 서비스(쓰기 없음).
 * 연동 가능 기관 목록 / 휴면 계좌 / 타사 보유 소수점 / 잠자는 잔돈 스캔 조회를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AssetQueryService {

    /** 설정에 threshold 없을 때 끝전 커팅 기본값(CMA 홈과 동일). */
    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.valueOf(10000);

    private final InstitutionMapper institutionMapper;
    private final LinkedAssetMapper linkedAssetMapper;
    private final ExternalHoldingMapper externalHoldingMapper;
    private final InternalAssetService internalAssetService;
    private final LedgerFeignClient ledgerFeignClient;

    public List<InstitutionResponse> getInstitutions(Long userId) {
        return institutionMapper.findInstitutions(userId);
    }

    public List<DormantAccountResponse> getDormantAccounts(Long userId) {
        return linkedAssetMapper.findDormantAccounts(userId);
    }

    /** 평면 행을 증권사(companyCode) 단위로 묶는다. 조회 순서(sort_order) 보존 위해 LinkedHashMap. */
    public List<ExternalHoldingResponse> getExternalHoldings(Long userId) {
        List<ExternalHoldingRow> rows = externalHoldingMapper.findExternalHoldings(userId);

        Map<String, ExternalHoldingResponse> grouped = new LinkedHashMap<>();
        for (ExternalHoldingRow row : rows) {
            ExternalHoldingResponse company = grouped.computeIfAbsent(
                    row.companyCode(),
                    code -> new ExternalHoldingResponse(row.companyCode(), row.companyName(), new ArrayList<>()));
            company.stocks().add(new ExternalHoldingResponse.Holding(
                    row.stockCode(), row.stockName(), row.quantity(), row.evaluated()));
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 잠자는 잔돈 스캔(연동 직후 발견 화면). 소스별 KRW 잔돈 + 총액.
     * ACCOUNT(끝전)/CARD(라운드업)/POINT는 CMA 홈 collectSources와 동일 계산을 공유한다(F-E):
     * ledger collection_settings(끝전 임계값·활성 소스)만 Feign read하고, 잔액 원천은 core 자체 DB로 로컬 계산.
     * FX(외화 환전 잔돈)는 수집 설정 대상이 아니므로 USD 지갑 잔액을 매매기준율로 KRW 환산한다.
     */
    public ScanResponse getScan(Long userId) {
        List<CollectionSettingView> settings = ledgerFeignClient.getCollectionSettings(userId);

        BigDecimal accountAmount = calcAccountAmount(userId, settings);
        BigDecimal cardAmount = calcCardAmount(userId, settings);
        BigDecimal pointAmount = calcPointAmount(userId, settings);
        BigDecimal fxAmount = calcFxAmount(userId);

        List<ScanResponse.Source> sources = List.of(
                new ScanResponse.Source("ACCOUNT", "신한은행 끝전", accountAmount),
                new ScanResponse.Source("CARD", "신한카드 잔돈", cardAmount),
                new ScanResponse.Source("POINT", "마이신한포인트 잔돈", pointAmount),
                new ScanResponse.Source("FX", "SOL트래블 환전 잔돈", fxAmount)
        );

        BigDecimal total = accountAmount.add(cardAmount).add(pointAmount).add(fxAmount);
        return new ScanResponse(total, sources);
    }

    /** 활성 ACCOUNT 설정의 연동 계좌별 끝전(balance % threshold) 합계. CmaQueryService.calcAccountAmount와 동일식. */
    private BigDecimal calcAccountAmount(Long userId, List<CollectionSettingView> settings) {
        List<CollectionSettingView> enabled = settings.stream()
                .filter(s -> "ACCOUNT".equals(s.sourceType()) && s.enabled())
                .toList();
        if (enabled.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<Long> enabledIds = enabled.stream().map(CollectionSettingView::sourceRefId).toList();
        Map<Long, BigDecimal> thresholdByRefId = enabled.stream()
                .collect(Collectors.toMap(
                        CollectionSettingView::sourceRefId,
                        s -> s.threshold() != null ? s.threshold() : DEFAULT_THRESHOLD));

        List<LinkedAccountSummary> accounts = internalAssetService.getLinkedAccounts(userId, enabledIds);
        return accounts.stream()
                .map(a -> a.balance().remainder(thresholdByRefId.getOrDefault(a.id(), DEFAULT_THRESHOLD)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 활성 CARD 설정들의 라운드업 잔돈 합계(다중 카드 합산). getCardRoundup 재사용(끝전 계산 단일 소스). */
    private BigDecimal calcCardAmount(Long userId, List<CollectionSettingView> settings) {
        return settings.stream()
                .filter(s -> "CARD".equals(s.sourceType()) && s.enabled())
                .map(s -> internalAssetService.getCardRoundup(userId, s.sourceRefId()).totalRoundupAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 활성 POINT 설정들의 포인트 잔액 합계(다중 포인트 합산). */
    private BigDecimal calcPointAmount(Long userId, List<CollectionSettingView> settings) {
        return settings.stream()
                .filter(s -> "POINT".equals(s.sourceType()) && s.enabled())
                .map(s -> internalAssetService.getAvailablePoints(userId, s.sourceRefId()).availablePoints())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 외화 지갑(USD) KRW 환산 = USD 잔액 × 매매기준율(정수 HALF_UP).
     * USD 미보유(0)면 환율을 조회하지 않아 KRW 전용 유저는 ledger 호출이 한 번 줄고,
     * 콜드스타트 502에도 영향받지 않는다(CMA totalKrwEquivalent과 동일 정책).
     */
    private BigDecimal calcFxAmount(Long userId) {
        BigDecimal usd = linkedAssetMapper.sumUsdWalletBalance(userId);
        if (usd == null || usd.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseRate = ledgerFeignClient.getUsdKrwRate().baseRate();
        return usd.multiply(baseRate).setScale(0, RoundingMode.HALF_UP);
    }
}
