package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.SourceDeduction;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaTransaction;
import com.pocketstock.ledger.cma.port.CmaChargePort;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CmaBankChargeService} 단위 테스트 — 은행→CMA 충전 핵심 로직(정확금액·txn-auth 없음).
 * 사용자 직접 충전과 trading 자동충전이 공유하는 이동/검증 규칙(소유·잔액·멱등·교차DB 차감)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CmaBankChargeServiceTest {

    @Mock private CmaAccountMapper accountMapper;
    @Mock private CmaTransactionMapper transactionMapper;
    @Mock private CmaLedgerWriter ledgerWriter;
    @Mock private AssetFeignClient feign;

    @InjectMocks private CmaBankChargeService service;

    private static final Long USER_ID = 1L;
    private static final Long CMA_ACC_ID = 100L;
    private static final Long SRC_ACC_ID = 7L;

    private CmaAccount cmaAccount() {
        CmaAccount a = new CmaAccount();
        a.setId(CMA_ACC_ID);
        a.setUserId(USER_ID);
        return a;
    }

    private LinkedAccountSummary bankAccount(String balance) {
        return new LinkedAccountSummary(SRC_ACC_ID, "BANK", new BigDecimal(balance), "KRW");
    }

    @Test
    @DisplayName("정상 충전: 정확금액 20,000을 은행에서 끌어와 DEPOSIT 기록·은행 차감하고 결과 반환")
    void charge_movesExactAmount() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(feign.getLinkedAccounts(USER_ID, List.of(SRC_ACC_ID))).thenReturn(List.of(bankAccount("100000")));
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(CMA_ACC_ID), eq("KRW"), eq("DEPOSIT"),
                eq("MANUAL"), eq(new BigDecimal("20000")), eq("LINKED_BANK_ACCOUNT"), eq(SRC_ACC_ID), eq("key-1")))
                .thenReturn(new BigDecimal("50000"));

        CmaChargePort.ChargeResult res = service.charge(USER_ID, SRC_ACC_ID, new BigDecimal("20000"), "key-1");

        assertThat(res.chargedAmount()).isEqualByComparingTo("20000");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("50000");
        verify(feign).deductAccountBalances(eq(USER_ID),
                eq(List.of(new SourceDeduction(SRC_ACC_ID, new BigDecimal("20000")))));
    }

    @Test
    @DisplayName("은행 잔액 부족: 충전 금액보다 은행 잔액이 작으면 INSUFFICIENT_BALANCE로 거부(원장 미기록)")
    void charge_bankInsufficient() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(feign.getLinkedAccounts(USER_ID, List.of(SRC_ACC_ID))).thenReturn(List.of(bankAccount("5000")));

        assertThatThrownBy(() -> service.charge(USER_ID, SRC_ACC_ID, new BigDecimal("20000"), "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        verify(ledgerWriter, never()).applyEntry(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), anyString());
        verify(feign, never()).deductAccountBalances(anyLong(), any());
    }

    @Test
    @DisplayName("미소유 계좌: 해당 사용자 소유 계좌가 아니면(결과 비면) INVALID_INPUT으로 거부")
    void charge_accountNotOwned() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(feign.getLinkedAccounts(USER_ID, List.of(SRC_ACC_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> service.charge(USER_ID, SRC_ACC_ID, new BigDecimal("20000"), "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("멱등 재요청: 같은 키 충전이 이미 있으면 재충전 없이 기존 거래값으로 결과 반환")
    void charge_idempotentReplay() {
        CmaTransaction existing = new CmaTransaction();
        existing.setUserId(USER_ID);
        existing.setAmount(new BigDecimal("20000"));
        existing.setBalanceAfter(new BigDecimal("50000"));
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(existing);

        CmaChargePort.ChargeResult res = service.charge(USER_ID, SRC_ACC_ID, new BigDecimal("20000"), "key-1");

        assertThat(res.chargedAmount()).isEqualByComparingTo("20000");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("50000");
        verify(ledgerWriter, never()).applyEntry(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), anyString());
        verify(feign, never()).deductAccountBalances(anyLong(), any());
    }

    @Test
    @DisplayName("CMA 계좌 없음: 충전 대상 계좌가 없으면 NOT_FOUND")
    void charge_noCmaAccount() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.charge(USER_ID, SRC_ACC_ID, new BigDecimal("20000"), "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("금액 검증: 0 또는 음수 충전 금액은 INVALID_INPUT")
    void charge_nonPositiveAmount() {
        assertThatThrownBy(() -> service.charge(USER_ID, SRC_ACC_ID, BigDecimal.ZERO, "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> service.charge(USER_ID, SRC_ACC_ID, new BigDecimal("-100"), "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
