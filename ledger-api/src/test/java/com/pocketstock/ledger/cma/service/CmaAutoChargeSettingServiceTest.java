package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.cma.domain.CmaAutoChargeSetting;
import com.pocketstock.ledger.cma.dto.request.AutoChargeSettingRequest;
import com.pocketstock.ledger.cma.dto.response.AutoChargeSettingResponse;
import com.pocketstock.ledger.cma.mapper.CmaAutoChargeSettingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CmaAutoChargeSettingService} 단위 테스트 — 빈 설정 기본값, ON 검증(필수·한도·소유),
 * OFF 검증 생략을 확인.
 */
@ExtendWith(MockitoExtension.class)
class CmaAutoChargeSettingServiceTest {

    @Mock private CmaAutoChargeSettingMapper settingMapper;
    @Mock private AssetFeignClient assetFeignClient;
    @InjectMocks private CmaAutoChargeSettingService service;

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    @Test
    @DisplayName("설정 행이 없는 신규 사용자는 OFF 기본값을 반환한다")
    void get_returnsDefaultWhenNoRow() {
        when(settingMapper.findByUserId(USER_ID)).thenReturn(null);

        AutoChargeSettingResponse response = service.get(USER_ID);

        assertThat(response.enabled()).isFalse();
        assertThat(response.sourceAccountId()).isNull();
        assertThat(response.maxChargePerTx()).isNull();
    }

    @Test
    @DisplayName("enabled=true: 본인 계좌·양수 한도면 정상 저장한다")
    void update_enabledSavesWhenOwnedAndValid() {
        when(assetFeignClient.getLinkedAccounts(eq(USER_ID), eq(List.of(ACCOUNT_ID))))
                .thenReturn(List.of(new LinkedAccountSummary(ACCOUNT_ID, "BANK", BigDecimal.valueOf(50000), "KRW")));

        service.update(USER_ID, new AutoChargeSettingRequest(true, ACCOUNT_ID, BigDecimal.valueOf(30000)));

        verify(settingMapper).upsert(any(CmaAutoChargeSetting.class));
    }

    @Test
    @DisplayName("enabled=true: 충전 재원이 남의 계좌면 INVALID_INPUT, 저장하지 않는다")
    void update_enabledRejectsForeignAccount() {
        when(assetFeignClient.getLinkedAccounts(eq(USER_ID), eq(List.of(ACCOUNT_ID))))
                .thenReturn(List.of());   // Feign이 userId로 필터 → 남의 계좌면 빈 배열

        assertThatThrownBy(() ->
                service.update(USER_ID, new AutoChargeSettingRequest(true, ACCOUNT_ID, BigDecimal.valueOf(30000))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(settingMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("enabled=true: 1회 한도가 0 이하면 INVALID_INPUT, Feign·저장 모두 호출하지 않는다")
    void update_enabledRejectsNonPositiveLimit() {
        assertThatThrownBy(() ->
                service.update(USER_ID, new AutoChargeSettingRequest(true, ACCOUNT_ID, BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(assetFeignClient, never()).getLinkedAccounts(any(), anyList());
        verify(settingMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("enabled=false: 검증 없이 끄기로 저장하고 Feign을 호출하지 않는다")
    void update_disabledSkipsValidation() {
        service.update(USER_ID, new AutoChargeSettingRequest(false, null, null));

        verify(assetFeignClient, never()).getLinkedAccounts(any(), anyList());
        verify(settingMapper).upsert(any(CmaAutoChargeSetting.class));
    }
}
