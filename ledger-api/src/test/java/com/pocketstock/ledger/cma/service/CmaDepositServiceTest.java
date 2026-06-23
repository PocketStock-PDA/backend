package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.SourceDeduction;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.domain.CmaTransaction;
import com.pocketstock.ledger.cma.dto.request.CmaDepositRequest;
import com.pocketstock.ledger.cma.dto.response.CmaDepositResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import com.pocketstock.user.security.TxnAuthGuard;
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
 * {@link CmaDepositService} 단위 테스트 — 부족분(목표 − CMA잔액)만 은행에서 끌어와 CMA 원화풀에 충전하는
 * 흐름과, 이미 충분/잔액 부족/미소유/멱등 재요청 분기를 검증.
 */
@ExtendWith(MockitoExtension.class)
class CmaDepositServiceTest {

    @Mock private CmaAccountMapper accountMapper;
    @Mock private CmaBalanceMapper balanceMapper;
    @Mock private CmaTransactionMapper transactionMapper;
    @Mock private CmaLedgerWriter ledgerWriter;
    @Mock private AssetFeignClient feign;
    @Mock private TxnAuthGuard txnAuthGuard;

    @InjectMocks private CmaDepositService service;

    private static final Long USER_ID = 1L;
    private static final Long CMA_ACC_ID = 100L;
    private static final Long SRC_ACC_ID = 7L;

    private CmaAccount cmaAccount() {
        CmaAccount a = new CmaAccount();
        a.setId(CMA_ACC_ID);
        a.setUserId(USER_ID);
        return a;
    }

    private CmaBalance krwPool(String balance) {
        CmaBalance b = new CmaBalance();
        b.setCmaAccountId(CMA_ACC_ID);
        b.setCurrency("KRW");
        b.setBalance(new BigDecimal(balance));
        return b;
    }

    private LinkedAccountSummary bankAccount(String balance) {
        return new LinkedAccountSummary(SRC_ACC_ID, "BANK", new BigDecimal(balance), "KRW");
    }

    private CmaDepositRequest request(String targetAmount) {
        return new CmaDepositRequest(new BigDecimal(targetAmount), SRC_ACC_ID, "key-1");
    }

    @Test
    @DisplayName("부족분 충전: 목표 50,000 - CMA 30,000 = 20,000을 은행에서 끌어와 DEPOSIT 기록·은행 차감")
    void deposit_chargesShortfall() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(balanceMapper.findByAccountIdAndCurrency(CMA_ACC_ID, "KRW")).thenReturn(krwPool("30000"));
        when(feign.getLinkedAccounts(USER_ID, List.of(SRC_ACC_ID))).thenReturn(List.of(bankAccount("100000")));
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(CMA_ACC_ID), eq("KRW"), eq("DEPOSIT"),
                eq("MANUAL"), eq(new BigDecimal("20000")), eq("LINKED_BANK_ACCOUNT"), eq(SRC_ACC_ID), eq("key-1")))
                .thenReturn(new BigDecimal("50000"));

        CmaDepositResponse res = service.deposit(USER_ID, request("50000"));

        assertThat(res.sufficient()).isFalse();
        assertThat(res.depositAmount()).isEqualByComparingTo("20000");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("50000");
        verify(txnAuthGuard).requireTxnAuth(USER_ID);
        verify(feign).deductAccountBalances(eq(USER_ID),
                eq(List.of(new SourceDeduction(SRC_ACC_ID, new BigDecimal("20000")))));
    }

    @Test
    @DisplayName("이미 충분: CMA 잔액이 목표 이상이면 이체하지 않고 sufficient=true 반환")
    void deposit_alreadySufficient() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(balanceMapper.findByAccountIdAndCurrency(CMA_ACC_ID, "KRW")).thenReturn(krwPool("80000"));

        CmaDepositResponse res = service.deposit(USER_ID, request("50000"));

        assertThat(res.sufficient()).isTrue();
        assertThat(res.depositAmount()).isEqualByComparingTo("0");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("80000");
        verify(ledgerWriter, never()).applyEntry(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), anyString());
        verify(feign, never()).deductAccountBalances(anyLong(), any());
    }

    @Test
    @DisplayName("은행 잔액 부족: 부족분보다 은행 잔액이 작으면 INSUFFICIENT_BALANCE로 거부(원장 미기록)")
    void deposit_bankInsufficient() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(balanceMapper.findByAccountIdAndCurrency(CMA_ACC_ID, "KRW")).thenReturn(krwPool("30000"));
        when(feign.getLinkedAccounts(USER_ID, List.of(SRC_ACC_ID))).thenReturn(List.of(bankAccount("5000")));

        assertThatThrownBy(() -> service.deposit(USER_ID, request("50000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        verify(ledgerWriter, never()).applyEntry(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), anyString());
        verify(feign, never()).deductAccountBalances(anyLong(), any());
    }

    @Test
    @DisplayName("미소유 계좌: 해당 사용자 소유 계좌가 아니면(결과 비면) INVALID_INPUT으로 거부")
    void deposit_accountNotOwned() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(balanceMapper.findByAccountIdAndCurrency(CMA_ACC_ID, "KRW")).thenReturn(krwPool("30000"));
        when(feign.getLinkedAccounts(USER_ID, List.of(SRC_ACC_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> service.deposit(USER_ID, request("50000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("멱등 재요청: 같은 키 충전이 이미 있으면 재적용 없이 기존 거래값으로 결과 반환")
    void deposit_idempotentReplay() {
        CmaTransaction existing = new CmaTransaction();
        existing.setUserId(USER_ID);
        existing.setAmount(new BigDecimal("20000"));
        existing.setBalanceAfter(new BigDecimal("50000"));
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(existing);

        CmaDepositResponse res = service.deposit(USER_ID, request("50000"));

        assertThat(res.depositAmount()).isEqualByComparingTo("20000");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("50000");
        verify(txnAuthGuard, never()).requireTxnAuth(anyLong());
        verify(ledgerWriter, never()).applyEntry(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("CMA 계좌 없음: 충전 대상 계좌가 없으면 NOT_FOUND")
    void deposit_noCmaAccount() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.deposit(USER_ID, request("50000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }
}
