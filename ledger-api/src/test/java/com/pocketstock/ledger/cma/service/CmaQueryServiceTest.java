package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.dto.response.CmaBalanceResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import com.pocketstock.ledger.exchange.dto.response.ExchangeRateResponse;
import com.pocketstock.ledger.exchange.service.ExchangeRateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CmaQueryService#getBalance} 단위 테스트 — totalKrwEquivalent USD 환산 합산 검증.
 * 매매기준율(base) 적용, USD 미보유 시 환율 미조회, 콜드스타트(USD>0+환율없음) 시 502.
 */
@ExtendWith(MockitoExtension.class)
class CmaQueryServiceTest {

    @Mock private CmaAccountMapper accountMapper;
    @Mock private CmaBalanceMapper balanceMapper;
    @Mock private CmaTransactionMapper transactionMapper;
    @Mock private CollectionSettingMapper settingMapper;
    @Mock private AssetFeignClient assetFeignClient;
    @Mock private ExchangeRateService exchangeRateService;

    private static final Long USER_ID = 1L;
    private static final Long CMA_ACC_ID = 100L;

    private CmaQueryService service() {
        return new CmaQueryService(accountMapper, balanceMapper, transactionMapper,
                settingMapper, assetFeignClient, exchangeRateService);
    }

    private CmaAccount cmaAccount() {
        CmaAccount a = new CmaAccount();
        a.setId(CMA_ACC_ID);
        a.setUserId(USER_ID);
        return a;
    }

    private CmaBalance balance(String currency, String amount, String rate) {
        return new CmaBalance(1L, CMA_ACC_ID, currency,
                new BigDecimal(amount), new BigDecimal(rate), LocalDateTime.now());
    }

    private ExchangeRateResponse rate(String base) {
        return new ExchangeRateResponse("USD", "KRW", new BigDecimal(base),
                new BigDecimal(base), new BigDecimal(base), BigDecimal.valueOf(0.90),
                BigDecimal.ZERO, "2026-06-22T09:00:00");
    }

    @Test
    @DisplayName("USD 지갑이 있으면 매매기준율로 환산해 KRW와 합산한다")
    void totalIncludesUsdAtBaseRate() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(balanceMapper.findByAccountId(CMA_ACC_ID)).thenReturn(List.of(
                balance("KRW", "1250000", "0.0350"),
                balance("USD", "250.00", "0.0420")
        ));
        when(exchangeRateService.getUsdKrwRate()).thenReturn(rate("1378"));

        CmaBalanceResponse res = service().getBalance(USER_ID);

        // 1,250,000 + 250 × 1378 = 1,250,000 + 344,500 = 1,594,500
        assertThat(res.totalKrwEquivalent()).isEqualByComparingTo("1594500");
        assertThat(res.accounts()).hasSize(2);
    }

    @Test
    @DisplayName("USD 미보유면 환율을 조회하지 않고 KRW만 합산한다")
    void krwOnlyDoesNotCallRate() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(balanceMapper.findByAccountId(CMA_ACC_ID)).thenReturn(List.of(
                balance("KRW", "405490", "0.0350")
        ));

        CmaBalanceResponse res = service().getBalance(USER_ID);

        assertThat(res.totalKrwEquivalent()).isEqualByComparingTo("405490");
        verify(exchangeRateService, never()).getUsdKrwRate();
    }

    @Test
    @DisplayName("USD>0 인데 환율 캐시가 비어 있으면 502가 전파된다")
    void coldStartWithUsdPropagates502() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(balanceMapper.findByAccountId(CMA_ACC_ID)).thenReturn(List.of(
                balance("KRW", "1000000", "0.0350"),
                balance("USD", "100.00", "0.0420")
        ));
        when(exchangeRateService.getUsdKrwRate())
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환율 정보를 아직 받지 못했습니다."));

        assertThatThrownBy(() -> service().getBalance(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("계좌가 없으면 NOT_FOUND")
    void accountNotFound() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service().getBalance(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
