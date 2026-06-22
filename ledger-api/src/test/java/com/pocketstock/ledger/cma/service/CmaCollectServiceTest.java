package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.CardRoundupSummary;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.PointSummary;
import com.pocketstock.ledger.client.dto.SourceDeduction;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CollectionSetting;
import com.pocketstock.ledger.cma.dto.response.CollectResult;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CmaCollectService} 단위 테스트 — Feign 계산값을 받아 CmaLedgerWriter로 적립하는 흐름과
 * 통합 수집의 부분 성공 집계를 검증.
 */
@ExtendWith(MockitoExtension.class)
class CmaCollectServiceTest {

    @Mock private CollectionSettingMapper settingMapper;
    @Mock private CmaAccountMapper accountMapper;
    @Mock private CmaLedgerWriter ledgerWriter;
    @Mock private AssetFeignClient feign;

    private static final Long USER_ID = 1L;
    private static final Long CMA_ACC_ID = 100L;

    /** self는 collectAll 외엔 미사용 → 단건 테스트에선 null. */
    private CmaCollectService service(CmaCollectService self) {
        return new CmaCollectService(settingMapper, accountMapper, ledgerWriter, feign, self);
    }

    private CmaAccount cmaAccount() {
        CmaAccount a = new CmaAccount();
        a.setId(CMA_ACC_ID);
        a.setUserId(USER_ID);
        return a;
    }

    private CollectionSetting setting(String sourceType, Long refId, boolean enabled, String threshold) {
        CollectionSetting s = new CollectionSetting();
        s.setUserId(USER_ID);
        s.setSourceType(sourceType);
        s.setSourceRefId(refId);
        s.setIsEnabled(enabled);
        s.setThreshold(threshold != null ? new BigDecimal(threshold) : null);
        return s;
    }

    @Test
    @DisplayName("계좌 끝전: 잔액 % threshold 합산을 적립하고, 단일 계좌면 ref_id에 그 계좌 id를 남긴다")
    void collectFromAccount_singleAccount() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(settingMapper.findByUserId(USER_ID))
                .thenReturn(List.of(setting("ACCOUNT", 11L, true, "10000")));
        when(feign.getLinkedAccounts(eq(USER_ID), eq(List.of(11L))))
                .thenReturn(List.of(new LinkedAccountSummary(11L, "CHECKING", new BigDecimal("37500"), "KRW")));
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(CMA_ACC_ID), eq("KRW"), eq("COLLECT"), eq("ACCOUNT"),
                eq(new BigDecimal("7500")), eq("LINKED_BANK_ACCOUNT"), eq(11L), eq("key-1")))
                .thenReturn(new BigDecimal("412990"));

        CollectResult result = service(null).collectFromAccount(USER_ID, "key-1");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.amount()).isEqualByComparingTo("7500");   // 37500 % 10000
        assertThat(result.balanceAfter()).isEqualByComparingTo("412990");
        // 수집한 끝전만큼 연동 계좌 잔액을 차감해 원천을 닫아야 한다(재수집/금액 복사 방지)
        verify(feign).deductAccountBalances(USER_ID, List.of(new SourceDeduction(11L, new BigDecimal("7500"))));
    }

    @Test
    @DisplayName("계좌 끝전: 여러 계좌면 계좌별 끝전을 각각 차감 대상으로 모아 원천을 닫는다")
    void collectFromAccount_deductsPerAccount() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(settingMapper.findByUserId(USER_ID)).thenReturn(List.of(
                setting("ACCOUNT", 11L, true, "10000"),
                setting("ACCOUNT", 12L, true, "10000")));
        when(feign.getLinkedAccounts(eq(USER_ID), eq(List.of(11L, 12L)))).thenReturn(List.of(
                new LinkedAccountSummary(11L, "CHECKING", new BigDecimal("37500"), "KRW"),
                new LinkedAccountSummary(12L, "CHECKING", new BigDecimal("12300"), "KRW")));
        when(ledgerWriter.applyEntry(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("415290"));

        service(null).collectFromAccount(USER_ID, "key-1");

        verify(feign).deductAccountBalances(USER_ID, List.of(
                new SourceDeduction(11L, new BigDecimal("7500")),    // 37500 % 10000
                new SourceDeduction(12L, new BigDecimal("2300"))));  // 12300 % 10000
    }

    @Test
    @DisplayName("계좌 끝전: 여러 계좌면 합산하고 ref_id는 null(다출처)로 남긴다")
    void collectFromAccount_multiAccount_nullRefId() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(settingMapper.findByUserId(USER_ID)).thenReturn(List.of(
                setting("ACCOUNT", 11L, true, "10000"),
                setting("ACCOUNT", 12L, true, "10000")));
        when(feign.getLinkedAccounts(eq(USER_ID), eq(List.of(11L, 12L)))).thenReturn(List.of(
                new LinkedAccountSummary(11L, "CHECKING", new BigDecimal("37500"), "KRW"),
                new LinkedAccountSummary(12L, "CHECKING", new BigDecimal("12300"), "KRW")));
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(CMA_ACC_ID), eq("KRW"), eq("COLLECT"), eq("ACCOUNT"),
                eq(new BigDecimal("9800")), eq("LINKED_BANK_ACCOUNT"), isNull(), eq("key-1")))
                .thenReturn(new BigDecimal("415290"));

        CollectResult result = service(null).collectFromAccount(USER_ID, "key-1");

        assertThat(result.amount()).isEqualByComparingTo("9800");   // 7500 + 2300
    }

    @Test
    @DisplayName("계좌 끝전: 수집할 잔돈이 0이면 INVALID_INPUT을 던지고 적립하지 않는다")
    void collectFromAccount_nothingCollectible() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(settingMapper.findByUserId(USER_ID))
                .thenReturn(List.of(setting("ACCOUNT", 11L, true, "10000")));
        when(feign.getLinkedAccounts(eq(USER_ID), eq(List.of(11L))))
                .thenReturn(List.of(new LinkedAccountSummary(11L, "CHECKING", new BigDecimal("30000"), "KRW")));

        assertThatThrownBy(() -> service(null).collectFromAccount(USER_ID, "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verify(ledgerWriter, never()).applyEntry(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("카드 라운드업: 적립 후 core-api에 수집 완료(mark-collected)를 호출한다")
    void collectFromCard_marksCollected() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(settingMapper.findByUserId(USER_ID))
                .thenReturn(List.of(setting("CARD", 22L, true, null)));
        when(feign.getCardRoundup(USER_ID, 22L))
                .thenReturn(new CardRoundupSummary(new BigDecimal("280"), List.of(1L, 2L)));
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(CMA_ACC_ID), eq("KRW"), eq("COLLECT"), eq("CARD"),
                eq(new BigDecimal("280")), eq("LINKED_CARD"), eq(22L), eq("key-c")))
                .thenReturn(new BigDecimal("100280"));

        CollectResult result = service(null).collectFromCard(USER_ID, "key-c");

        assertThat(result.amount()).isEqualByComparingTo("280");
        verify(feign).markRoundupCollected(USER_ID, List.of(1L, 2L));
    }

    @Test
    @DisplayName("카드 라운드업(다중 카드): 활성 카드 전부 합산하고 거래도 전부 mark, ref_id는 null(다출처)")
    void collectFromCard_multipleCards() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(settingMapper.findByUserId(USER_ID)).thenReturn(List.of(
                setting("CARD", 22L, true, null),
                setting("CARD", 23L, true, null)));
        when(feign.getCardRoundup(USER_ID, 22L))
                .thenReturn(new CardRoundupSummary(new BigDecimal("280"), List.of(1L, 2L)));
        when(feign.getCardRoundup(USER_ID, 23L))
                .thenReturn(new CardRoundupSummary(new BigDecimal("520"), List.of(3L)));
        // 합산 800, ref_id=null(여러 장)
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(CMA_ACC_ID), eq("KRW"), eq("COLLECT"), eq("CARD"),
                eq(new BigDecimal("800")), eq("LINKED_CARD"), eq((Long) null), eq("key-cc")))
                .thenReturn(new BigDecimal("100800"));

        CollectResult result = service(null).collectFromCard(USER_ID, "key-cc");

        assertThat(result.amount()).isEqualByComparingTo("800");
        verify(feign).markRoundupCollected(USER_ID, List.of(1L, 2L, 3L)); // 두 카드 거래 전부
    }

    @Test
    @DisplayName("포인트: 전환 가능 포인트를 적립한다")
    void collectFromPoint() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(settingMapper.findByUserId(USER_ID))
                .thenReturn(List.of(setting("POINT", 33L, true, null)));
        when(feign.getAvailablePoints(USER_ID, 33L))
                .thenReturn(new PointSummary(33L, new BigDecimal("5000")));
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(CMA_ACC_ID), eq("KRW"), eq("COLLECT"), eq("POINT"),
                eq(new BigDecimal("5000")), eq("LINKED_POINT"), eq(33L), eq("key-p")))
                .thenReturn(new BigDecimal("105000"));

        CollectResult result = service(null).collectFromPoint(USER_ID, "key-p");

        assertThat(result.amount()).isEqualByComparingTo("5000");
        // 수집한 포인트만큼 연동 포인트 잔액을 차감해 원천을 닫아야 한다
        verify(feign).deductPointBalances(USER_ID, List.of(new SourceDeduction(33L, new BigDecimal("5000"))));
    }

    @Test
    @DisplayName("통합 수집: 소스별 독립 실행, 성공/건너뜀이 섞여도 모두 결과로 모은다(부분 성공)")
    void collectAll_partialSuccess() {
        CmaCollectService self = mock(CmaCollectService.class);
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(self.collectFromAccount(eq(USER_ID), anyString()))
                .thenReturn(CollectResult.success("ACCOUNT", new BigDecimal("7500"), new BigDecimal("412990")));
        when(self.collectFromCard(eq(USER_ID), anyString()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "활성화된 카드 적립 소스가 없습니다."));
        when(self.collectFromPoint(eq(USER_ID), anyString()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "수집 가능한 잔돈이 없습니다."));

        List<CollectResult> results = service(self).collectAll(USER_ID, "base-key");

        assertThat(results).hasSize(3);
        assertThat(results.get(0).sourceType()).isEqualTo("ACCOUNT");
        assertThat(results.get(0).status()).isEqualTo("SUCCESS");
        assertThat(results.get(1).status()).isEqualTo("SKIPPED");
        assertThat(results.get(2).status()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("통합 수집: INVALID_INPUT이 아닌 비즈니스 오류는 SKIPPED가 아니라 FAILED로 분류한다")
    void collectAll_nonInvalidInputIsFailed() {
        CmaCollectService self = mock(CmaCollectService.class);
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(self.collectFromAccount(eq(USER_ID), anyString()))
                .thenReturn(CollectResult.success("ACCOUNT", new BigDecimal("7500"), new BigDecimal("412990")));
        when(self.collectFromCard(eq(USER_ID), anyString()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌를 찾을 수 없습니다."));  // 실제 오류
        when(self.collectFromPoint(eq(USER_ID), anyString()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "수집 가능한 잔돈이 없습니다."));

        List<CollectResult> results = service(self).collectAll(USER_ID, "base-key");

        assertThat(results.get(0).status()).isEqualTo("SUCCESS");
        assertThat(results.get(1).status()).isEqualTo("FAILED");   // NOT_FOUND → 가려지지 않음
        assertThat(results.get(2).status()).isEqualTo("SKIPPED");  // INVALID_INPUT → 정상 건너뜀
    }

    @Test
    @DisplayName("통합 수집: baseKey에 소스 접미사를 붙인 멱등키로 각 소스를 호출한다")
    void collectAll_derivesPerSourceKey() {
        CmaCollectService self = mock(CmaCollectService.class);
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(self.collectFromAccount(eq(USER_ID), eq("base:ACCOUNT")))
                .thenReturn(CollectResult.success("ACCOUNT", new BigDecimal("7500"), new BigDecimal("412990")));
        when(self.collectFromCard(eq(USER_ID), eq("base:CARD")))
                .thenReturn(CollectResult.success("CARD", new BigDecimal("280"), new BigDecimal("413270")));
        when(self.collectFromPoint(eq(USER_ID), eq("base:POINT")))
                .thenReturn(CollectResult.success("POINT", new BigDecimal("5000"), new BigDecimal("418270")));

        List<CollectResult> results = service(self).collectAll(USER_ID, "base");

        assertThat(results).extracting(CollectResult::status).containsExactly("SUCCESS", "SUCCESS", "SUCCESS");
        verify(self).collectFromAccount(USER_ID, "base:ACCOUNT");
        verify(self).collectFromCard(USER_ID, "base:CARD");
        verify(self).collectFromPoint(USER_ID, "base:POINT");
    }

    @Test
    @DisplayName("CMA 계좌가 없으면 수집은 NOT_FOUND를 던진다")
    void collectFromAccount_noCmaAccount() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service(null).collectFromAccount(USER_ID, "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }
}
