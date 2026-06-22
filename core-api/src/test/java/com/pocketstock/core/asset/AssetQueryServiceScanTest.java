package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.ScanResponse;
import com.pocketstock.core.asset.mapper.ExternalHoldingMapper;
import com.pocketstock.core.asset.mapper.InstitutionMapper;
import com.pocketstock.core.asset.mapper.LinkedAssetMapper;
import com.pocketstock.core.client.LedgerFeignClient;
import com.pocketstock.core.client.dto.CollectionSettingView;
import com.pocketstock.core.client.dto.UsdKrwRateView;
import com.pocketstock.core.internal.asset.InternalAssetService;
import com.pocketstock.core.internal.asset.dto.CardRoundupSummary;
import com.pocketstock.core.internal.asset.dto.LinkedAccountSummary;
import com.pocketstock.core.internal.asset.dto.PointSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AssetQueryService#getScan} 단위 테스트 — 잠자는 잔돈 스캔.
 * ACCOUNT 끝전(설정별 threshold)·CARD 라운드업·POINT·FX(USD×매매기준율) 합산과
 * USD 미보유 시 환율 미조회를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AssetQueryServiceScanTest {

    @Mock private InstitutionMapper institutionMapper;
    @Mock private LinkedAssetMapper linkedAssetMapper;
    @Mock private ExternalHoldingMapper externalHoldingMapper;
    @Mock private InternalAssetService internalAssetService;
    @Mock private LedgerFeignClient ledgerFeignClient;

    @InjectMocks private AssetQueryService service;

    private static final Long USER_ID = 1L;

    private ScanResponse.Source source(ScanResponse res, String type) {
        return res.sources().stream().filter(s -> s.sourceType().equals(type)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("4개 소스(ACCOUNT 끝전·CARD 라운드업·POINT·FX 환산)를 합산한다")
    void getScan_sumsAllSources() {
        // ACCOUNT: 잔액 12,300 % threshold 10,000 = 2,300
        when(ledgerFeignClient.getCollectionSettings(USER_ID)).thenReturn(List.of(
                new CollectionSettingView("ACCOUNT", 1L, true, BigDecimal.valueOf(10000)),
                new CollectionSettingView("CARD", 10L, true, null),
                new CollectionSettingView("POINT", 20L, true, null)
        ));
        when(internalAssetService.getLinkedAccounts(USER_ID, List.of(1L))).thenReturn(List.of(
                new LinkedAccountSummary(1L, "DEMAND", BigDecimal.valueOf(12300), "KRW")));
        when(internalAssetService.getCardRoundup(USER_ID, 10L))
                .thenReturn(new CardRoundupSummary(BigDecimal.valueOf(700), List.of(5L)));
        when(internalAssetService.getAvailablePoints(USER_ID, 20L))
                .thenReturn(new PointSummary(20L, BigDecimal.valueOf(28000)));
        // FX: 4.50 USD × 1378 = 6201
        when(linkedAssetMapper.sumUsdWalletBalance(USER_ID)).thenReturn(new BigDecimal("4.5000"));
        when(ledgerFeignClient.getUsdKrwRate()).thenReturn(new UsdKrwRateView(new BigDecimal("1378")));

        ScanResponse res = service.getScan(USER_ID);

        assertThat(source(res, "ACCOUNT").amount()).isEqualByComparingTo("2300");
        assertThat(source(res, "CARD").amount()).isEqualByComparingTo("700");
        assertThat(source(res, "POINT").amount()).isEqualByComparingTo("28000");
        assertThat(source(res, "FX").amount()).isEqualByComparingTo("6201");
        assertThat(res.totalAmount()).isEqualByComparingTo("37201"); // 2300+700+28000+6201
    }

    @Test
    @DisplayName("설정별 threshold(1000/5000)가 계좌마다 다르게 적용된다")
    void getScan_perAccountThreshold() {
        when(ledgerFeignClient.getCollectionSettings(USER_ID)).thenReturn(List.of(
                new CollectionSettingView("ACCOUNT", 1L, true, BigDecimal.valueOf(5000)),
                new CollectionSettingView("ACCOUNT", 2L, true, BigDecimal.valueOf(1000))
        ));
        when(internalAssetService.getLinkedAccounts(USER_ID, List.of(1L, 2L))).thenReturn(List.of(
                new LinkedAccountSummary(1L, "DEMAND", BigDecimal.valueOf(12300), "KRW"),  // %5000 = 2300
                new LinkedAccountSummary(2L, "DEMAND", BigDecimal.valueOf(85700), "KRW"))); // %1000 = 700
        when(linkedAssetMapper.sumUsdWalletBalance(USER_ID)).thenReturn(BigDecimal.ZERO);

        ScanResponse res = service.getScan(USER_ID);

        assertThat(source(res, "ACCOUNT").amount()).isEqualByComparingTo("3000"); // 2300 + 700
    }

    @Test
    @DisplayName("다중 카드/포인트 설정은 findFirst가 아니라 전부 합산한다")
    void getScan_sumsMultipleCardAndPoint() {
        when(ledgerFeignClient.getCollectionSettings(USER_ID)).thenReturn(List.of(
                new CollectionSettingView("CARD", 10L, true, null),
                new CollectionSettingView("CARD", 11L, true, null),
                new CollectionSettingView("POINT", 20L, true, null),
                new CollectionSettingView("POINT", 21L, true, null)
        ));
        when(internalAssetService.getCardRoundup(USER_ID, 10L))
                .thenReturn(new CardRoundupSummary(BigDecimal.valueOf(280), List.of(1L)));
        when(internalAssetService.getCardRoundup(USER_ID, 11L))
                .thenReturn(new CardRoundupSummary(BigDecimal.valueOf(520), List.of(2L)));
        when(internalAssetService.getAvailablePoints(USER_ID, 20L))
                .thenReturn(new PointSummary(20L, BigDecimal.valueOf(28000)));
        when(internalAssetService.getAvailablePoints(USER_ID, 21L))
                .thenReturn(new PointSummary(21L, BigDecimal.valueOf(5000)));
        when(linkedAssetMapper.sumUsdWalletBalance(USER_ID)).thenReturn(BigDecimal.ZERO);

        ScanResponse res = service.getScan(USER_ID);

        assertThat(source(res, "CARD").amount()).isEqualByComparingTo("800");   // 280 + 520
        assertThat(source(res, "POINT").amount()).isEqualByComparingTo("33000"); // 28000 + 5000
    }

    @Test
    @DisplayName("비활성 소스는 0으로 집계되고 Feign 계산 호출도 하지 않는다")
    void getScan_disabledSourcesSkipped() {
        when(ledgerFeignClient.getCollectionSettings(USER_ID)).thenReturn(List.of(
                new CollectionSettingView("ACCOUNT", 1L, false, BigDecimal.valueOf(10000)),
                new CollectionSettingView("CARD", 10L, false, null)
        ));
        when(linkedAssetMapper.sumUsdWalletBalance(USER_ID)).thenReturn(BigDecimal.ZERO);

        ScanResponse res = service.getScan(USER_ID);

        assertThat(res.totalAmount()).isEqualByComparingTo("0");
        verify(internalAssetService, never()).getLinkedAccounts(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(internalAssetService, never()).getCardRoundup(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("USD 미보유면 환율을 조회하지 않고 FX=0")
    void getScan_noUsd_skipsRate() {
        when(ledgerFeignClient.getCollectionSettings(USER_ID)).thenReturn(List.of());
        when(linkedAssetMapper.sumUsdWalletBalance(USER_ID)).thenReturn(BigDecimal.ZERO);

        ScanResponse res = service.getScan(USER_ID);

        assertThat(source(res, "FX").amount()).isEqualByComparingTo("0");
        verify(ledgerFeignClient, never()).getUsdKrwRate();
    }
}
