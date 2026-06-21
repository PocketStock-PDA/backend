package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.exchange.port.FxLegResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CmaFundsAdapter} 단위 테스트 — 환전 양다리를 {@link CmaLedgerWriter}로
 * FX_OUT → FX_IN 순서·멱등키·계좌 없음 처리로 위임하는지 검증.
 */
@ExtendWith(MockitoExtension.class)
class CmaFundsAdapterTest {

    @Mock
    private CmaAccountMapper accountMapper;
    @Mock
    private CmaLedgerWriter ledgerWriter;
    @InjectMocks
    private CmaFundsAdapter adapter;

    private static final Long USER_ID = 1L;
    private static final Long ACC_ID = 10L;
    private static final Long FX_TX_ID = 99L;

    private CmaAccount account() {
        CmaAccount a = new CmaAccount();
        a.setId(ACC_ID);
        a.setUserId(USER_ID);
        return a;
    }

    @Test
    @DisplayName("KRW→USD: 출금레그(FX_OUT, 음수)를 먼저, 입금레그(FX_IN, 양수)를 나중에 기록하고 잔액을 반환한다")
    void applyFxLegs_krwToUsd_orderAndKeys() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(account());
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(ACC_ID), eq("KRW"), eq("FX_OUT"), eq("SYSTEM"),
                eq(new BigDecimal("-100000")), eq("FX_TX"), eq(FX_TX_ID), eq("FX:99:OUT")))
                .thenReturn(new BigDecimal("305490"));
        when(ledgerWriter.applyEntry(eq(USER_ID), eq(ACC_ID), eq("USD"), eq("FX_IN"), eq("SYSTEM"),
                eq(new BigDecimal("70.12")), eq("FX_TX"), eq(FX_TX_ID), eq("FX:99:IN")))
                .thenReturn(new BigDecimal("70.12"));

        FxLegResult result = adapter.applyFxLegs(USER_ID,
                "KRW", new BigDecimal("100000"),
                "USD", new BigDecimal("70.12"),
                FX_TX_ID);

        assertThat(result.remainFrom()).isEqualByComparingTo("305490");
        assertThat(result.remainTo()).isEqualByComparingTo("70.12");

        // 출금 레그가 입금 레그보다 먼저 호출되어야 함(잔액 부족 시 입금 전에 실패)
        InOrder inOrder = inOrder(ledgerWriter);
        inOrder.verify(ledgerWriter).applyEntry(eq(USER_ID), eq(ACC_ID), eq("KRW"), eq("FX_OUT"),
                eq("SYSTEM"), eq(new BigDecimal("-100000")), eq("FX_TX"), eq(FX_TX_ID), eq("FX:99:OUT"));
        inOrder.verify(ledgerWriter).applyEntry(eq(USER_ID), eq(ACC_ID), eq("USD"), eq("FX_IN"),
                eq("SYSTEM"), eq(new BigDecimal("70.12")), eq("FX_TX"), eq(FX_TX_ID), eq("FX:99:IN"));
    }

    @Test
    @DisplayName("fxTransactionId가 null이면 INVALID_INPUT을 던진다(FX:null 멱등키 충돌 방지)")
    void applyFxLegs_nullTxId() {
        assertThatThrownBy(() -> adapter.applyFxLegs(USER_ID,
                "KRW", new BigDecimal("100000"), "USD", new BigDecimal("70.12"), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verify(ledgerWriter, never()).applyEntry(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("금액이 0 이하이면 INVALID_INPUT을 던진다(부호 역전 방지)")
    void applyFxLegs_nonPositiveAmount() {
        assertThatThrownBy(() -> adapter.applyFxLegs(USER_ID,
                "KRW", new BigDecimal("-100000"), "USD", new BigDecimal("70.12"), FX_TX_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verify(ledgerWriter, never()).applyEntry(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CMA 계좌가 없으면 NOT_FOUND를 던지고 원장을 건드리지 않는다")
    void applyFxLegs_noAccount() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> adapter.applyFxLegs(USER_ID,
                "KRW", new BigDecimal("100000"),
                "USD", new BigDecimal("70.12"),
                FX_TX_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        verify(ledgerWriter, never()).applyEntry(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
