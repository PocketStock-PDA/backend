package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.domain.CmaTransaction;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CmaLedgerWriter} 단위 테스트 — 매퍼를 목킹해 원장 쓰기 핵심 규칙을 격리 검증.
 * (DB·Redis·LS 불필요. 잠금/멱등/출금가드/lazy 생성/부호 규약 위주.)
 */
@ExtendWith(MockitoExtension.class)
class CmaLedgerWriterTest {

    @Mock
    private CmaBalanceMapper balanceMapper;
    @Mock
    private CmaTransactionMapper transactionMapper;
    @InjectMocks
    private CmaLedgerWriter writer;

    private static final Long USER_ID = 1L;
    private static final Long ACC_ID = 10L;

    private CmaBalance wallet(String currency, String balance, String rate) {
        CmaBalance b = new CmaBalance();
        b.setCmaAccountId(ACC_ID);
        b.setCurrency(currency);
        b.setBalance(new BigDecimal(balance));
        b.setInterestRate(new BigDecimal(rate));
        return b;
    }

    @Nested
    @DisplayName("입금(+)")
    class Deposit {

        @Test
        @DisplayName("기존 잔액에 더하고 balance_after를 반환하며, 원장 append + 잔액 갱신을 모두 수행한다")
        void deposit_addsAndPersists() {
            when(transactionMapper.findByIdempotencyKey("k1")).thenReturn(null);
            when(balanceMapper.findByAccountIdAndCurrencyForUpdate(ACC_ID, "KRW"))
                    .thenReturn(wallet("KRW", "405490", "0.0350"));
            when(transactionMapper.insert(any())).thenReturn(1);

            BigDecimal after = writer.applyEntry(USER_ID, ACC_ID, "KRW",
                    "COLLECT", "ACCOUNT", new BigDecimal("7500"),
                    "LINKED_BANK_ACCOUNT", 3L, "k1");

            assertThat(after).isEqualByComparingTo("412990");

            ArgumentCaptor<CmaTransaction> txCaptor = ArgumentCaptor.forClass(CmaTransaction.class);
            verify(transactionMapper).insert(txCaptor.capture());
            CmaTransaction tx = txCaptor.getValue();
            assertThat(tx.getAmount()).isEqualByComparingTo("7500");
            assertThat(tx.getBalanceAfter()).isEqualByComparingTo("412990");
            assertThat(tx.getTxType()).isEqualTo("COLLECT");

            ArgumentCaptor<CmaBalance> balCaptor = ArgumentCaptor.forClass(CmaBalance.class);
            verify(balanceMapper).upsertBalance(balCaptor.capture());
            assertThat(balCaptor.getValue().getBalance()).isEqualByComparingTo("412990");
            // 읽어 둔 이율 유지(덮어쓰기 아님)
            assertThat(balCaptor.getValue().getInterestRate()).isEqualByComparingTo("0.0350");
        }

        @Test
        @DisplayName("지갑이 없던 통화(USD)도 lazy 생성(INSERT IGNORE) 후 0원 기준으로 입금된다")
        void deposit_lazyCreatesWallet() {
            when(transactionMapper.findByIdempotencyKey(any())).thenReturn(null);
            // insertBalance(IGNORE) 직후 잠금 읽기에서 0원 지갑이 잡힌다
            when(balanceMapper.findByAccountIdAndCurrencyForUpdate(ACC_ID, "USD"))
                    .thenReturn(wallet("USD", "0", "0.0420"));
            when(transactionMapper.insert(any())).thenReturn(1);

            BigDecimal after = writer.applyEntry(USER_ID, ACC_ID, "USD",
                    "FX_IN", "SYSTEM", new BigDecimal("45.67"),
                    "FX_TX", 99L, "FX:99:IN");

            assertThat(after).isEqualByComparingTo("45.67");
            verify(balanceMapper).insertBalance(any(CmaBalance.class));   // lazy 생성 시도
        }
    }

    @Nested
    @DisplayName("출금(-)")
    class Withdraw {

        @Test
        @DisplayName("잔액이 충분하면 차감하고 음수 amount로 기록한다")
        void withdraw_ok() {
            when(transactionMapper.findByIdempotencyKey(any())).thenReturn(null);
            when(balanceMapper.findByAccountIdAndCurrencyForUpdate(ACC_ID, "KRW"))
                    .thenReturn(wallet("KRW", "100000", "0.0350"));
            when(transactionMapper.insert(any())).thenReturn(1);

            BigDecimal after = writer.applyEntry(USER_ID, ACC_ID, "KRW",
                    "FX_OUT", "SYSTEM", new BigDecimal("-30000"),
                    "FX_TX", 99L, "FX:99:OUT");

            assertThat(after).isEqualByComparingTo("70000");
        }

        @Test
        @DisplayName("잔액보다 큰 출금이면 INSUFFICIENT_BALANCE를 던지고 원장/잔액을 손대지 않는다")
        void withdraw_insufficient() {
            when(transactionMapper.findByIdempotencyKey(any())).thenReturn(null);
            when(balanceMapper.findByAccountIdAndCurrencyForUpdate(ACC_ID, "USD"))
                    .thenReturn(wallet("USD", "10.00", "0.0420"));

            assertThatThrownBy(() -> writer.applyEntry(USER_ID, ACC_ID, "USD",
                    "FX_OUT", "SYSTEM", new BigDecimal("-50.00"),
                    "FX_TX", 99L, "FX:99:OUT"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

            verify(transactionMapper, never()).insert(any());
            verify(balanceMapper, never()).upsertBalance(any());
        }
    }

    @Nested
    @DisplayName("멱등")
    class Idempotency {

        @Test
        @DisplayName("동일 키가 이미 있으면 잔액을 다시 손대지 않고 기존 balance_after를 반환한다")
        void replay_returnsExistingBalance() {
            CmaTransaction existing = new CmaTransaction();
            existing.setBalanceAfter(new BigDecimal("412990"));
            when(transactionMapper.findByIdempotencyKey("k1")).thenReturn(existing);

            BigDecimal after = writer.applyEntry(USER_ID, ACC_ID, "KRW",
                    "COLLECT", "ACCOUNT", new BigDecimal("7500"),
                    "LINKED_BANK_ACCOUNT", 3L, "k1");

            assertThat(after).isEqualByComparingTo("412990");
            // 잠금 읽기·append·잔액 갱신 어느 것도 일어나지 않음
            verify(balanceMapper, never()).findByAccountIdAndCurrencyForUpdate(any(), any());
            verify(transactionMapper, never()).insert(any());
            verify(balanceMapper, never()).upsertBalance(any());
        }

        @Test
        @DisplayName("선행조회엔 없었지만 insert 경합으로 신규가 아니면(affected!=1) 잔액 갱신을 건너뛴다")
        void insertRace_skipsBalanceUpdate() {
            CmaTransaction raced = new CmaTransaction();
            raced.setBalanceAfter(new BigDecimal("412990"));
            // 1) 선행조회 null → 2) 잠금읽기 → 3) insert가 0(경합) → 4) 재조회로 기존값
            when(transactionMapper.findByIdempotencyKey("k1")).thenReturn(null, raced);
            when(balanceMapper.findByAccountIdAndCurrencyForUpdate(ACC_ID, "KRW"))
                    .thenReturn(wallet("KRW", "405490", "0.0350"));
            when(transactionMapper.insert(any())).thenReturn(0);

            BigDecimal after = writer.applyEntry(USER_ID, ACC_ID, "KRW",
                    "COLLECT", "ACCOUNT", new BigDecimal("7500"),
                    "LINKED_BANK_ACCOUNT", 3L, "k1");

            assertThat(after).isEqualByComparingTo("412990");
            verify(balanceMapper, never()).upsertBalance(any());
        }

        @Test
        @DisplayName("키가 null이면 선행조회 없이 항상 신규로 기록한다")
        void nullKey_alwaysNew() {
            when(balanceMapper.findByAccountIdAndCurrencyForUpdate(ACC_ID, "KRW"))
                    .thenReturn(wallet("KRW", "0", "0.0350"));
            when(transactionMapper.insert(any())).thenReturn(1);

            writer.applyEntry(USER_ID, ACC_ID, "KRW",
                    "DEPOSIT", "MANUAL", new BigDecimal("10000"),
                    null, null, null);

            verify(transactionMapper, never()).findByIdempotencyKey(any());
            verify(transactionMapper).insert(any());
            verify(balanceMapper).upsertBalance(any());
        }
    }

    @Nested
    @DisplayName("금액 계약")
    class AmountContract {

        @Test
        @DisplayName("금액이 0이면 INVALID_INPUT을 던지고 원장/잔액을 손대지 않는다")
        void zeroAmount_rejected() {
            assertThatThrownBy(() -> writer.applyEntry(USER_ID, ACC_ID, "KRW",
                    "COLLECT", "ACCOUNT", BigDecimal.ZERO,
                    "LINKED_BANK_ACCOUNT", 3L, "k1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

            verify(transactionMapper, never()).insert(any());
            verify(balanceMapper, never()).upsertBalance(any());
        }

        @Test
        @DisplayName("금액이 null이면 INVALID_INPUT을 던진다(NPE 방지)")
        void nullAmount_rejected() {
            assertThatThrownBy(() -> writer.applyEntry(USER_ID, ACC_ID, "KRW",
                    "COLLECT", "ACCOUNT", null,
                    "LINKED_BANK_ACCOUNT", 3L, "k1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }
}
