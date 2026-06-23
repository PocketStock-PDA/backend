package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.domain.CmaTransaction;
import com.pocketstock.ledger.cma.dto.request.CmaDepositRequest;
import com.pocketstock.ledger.cma.dto.response.CmaDepositResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import com.pocketstock.ledger.cma.port.CmaChargePort;
import com.pocketstock.user.security.TxnAuthGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

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
 * {@link CmaDepositService} 단위 테스트 — 사용자 직접 충전 껍데기. txn-auth + 부족분 계산만 책임지고
 * 실제 은행→CMA 이동은 {@link CmaChargePort}에 위임하므로, 여기선 위임 인자/이미충분/멱등/계좌없음만 검증한다.
 * (소유·잔액 검증·원장 이동 자체는 {@link CmaBankChargeServiceTest}가 담당)
 */
@ExtendWith(MockitoExtension.class)
class CmaDepositServiceTest {

    @Mock private CmaAccountMapper accountMapper;
    @Mock private CmaBalanceMapper balanceMapper;
    @Mock private CmaTransactionMapper transactionMapper;
    @Mock private CmaChargePort chargePort;
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

    private CmaDepositRequest request(String targetAmount) {
        return new CmaDepositRequest(new BigDecimal(targetAmount), SRC_ACC_ID, "key-1");
    }

    @Test
    @DisplayName("부족분 위임: 목표 50,000 - CMA 30,000 = 20,000을 충전 포트로 위임하고 결과를 응답에 싣는다")
    void deposit_delegatesShortfallToPort() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(balanceMapper.findByAccountIdAndCurrency(CMA_ACC_ID, "KRW")).thenReturn(krwPool("30000"));
        when(chargePort.charge(USER_ID, SRC_ACC_ID, new BigDecimal("20000"), "key-1"))
                .thenReturn(new CmaChargePort.ChargeResult(new BigDecimal("20000"), new BigDecimal("50000")));

        CmaDepositResponse res = service.deposit(USER_ID, request("50000"));

        assertThat(res.sufficient()).isFalse();
        assertThat(res.depositAmount()).isEqualByComparingTo("20000");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("50000");
        verify(txnAuthGuard).requireTxnAuth(USER_ID);
        verify(chargePort).charge(USER_ID, SRC_ACC_ID, new BigDecimal("20000"), "key-1");
    }

    @Test
    @DisplayName("이미 충분: CMA 잔액이 목표 이상이면 충전 포트를 호출하지 않고 sufficient=true 반환")
    void deposit_alreadySufficient() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(balanceMapper.findByAccountIdAndCurrency(CMA_ACC_ID, "KRW")).thenReturn(krwPool("80000"));

        CmaDepositResponse res = service.deposit(USER_ID, request("50000"));

        assertThat(res.sufficient()).isTrue();
        assertThat(res.depositAmount()).isEqualByComparingTo("0");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("80000");
        verify(chargePort, never()).charge(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("멱등 재요청: 같은 키 충전이 이미 있으면 txn-auth·충전 없이 기존 거래값으로 결과 반환")
    void deposit_idempotentReplay() {
        CmaTransaction existing = depositTx("20000", "50000");
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(existing);

        CmaDepositResponse res = service.deposit(USER_ID, request("50000"));

        assertThat(res.depositAmount()).isEqualByComparingTo("20000");
        assertThat(res.cmaBalanceAfter()).isEqualByComparingTo("50000");
        verify(txnAuthGuard, never()).requireTxnAuth(anyLong());
        verify(chargePort, never()).charge(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("멱등키 오용: 같은 키가 다른 용도(COLLECT) 거래에 쓰였으면 충전 응답으로 오인하지 않고 409")
    void deposit_keyReusedByOtherOperation() {
        CmaTransaction collect = new CmaTransaction();
        collect.setUserId(USER_ID);
        collect.setTxType("COLLECT");
        collect.setSourceType("ACCOUNT");
        collect.setCurrency("KRW");
        collect.setAmount(new BigDecimal("5000"));
        collect.setBalanceAfter(new BigDecimal("410490"));
        when(accountMapper.findByUserId(USER_ID)).thenReturn(cmaAccount());
        when(transactionMapper.findByIdempotencyKey("key-1")).thenReturn(collect);

        assertThatThrownBy(() -> service.deposit(USER_ID, request("50000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        verify(chargePort, never()).charge(anyLong(), anyLong(), any(), anyString());
    }

    private CmaTransaction depositTx(String amount, String balanceAfter) {
        CmaTransaction tx = new CmaTransaction();
        tx.setUserId(USER_ID);
        tx.setTxType("DEPOSIT");
        tx.setSourceType("MANUAL");
        tx.setCurrency("KRW");
        tx.setAmount(new BigDecimal(amount));
        tx.setBalanceAfter(new BigDecimal(balanceAfter));
        return tx;
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
