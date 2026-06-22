package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.exchange.port.CmaFundsPort;
import com.pocketstock.ledger.exchange.port.FxLegResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link CmaFundsPort} 실 어댑터 (CMA 도메인, D4) — 환전 체결의 CMA 풀 반영을 실제 원장으로 처리한다.
 * 이 빈이 환전 CMA 레그의 유일한 구현이다(#56 완료, 임시 스텁 DevCmaFundsAdapter는 #57로 제거).
 *
 * <p>환전 양다리를 {@link CmaLedgerWriter}로 두 줄 기록한다 — from풀 차감(FX_OUT, 음수) +
 * to풀 입금(FX_IN, 양수). 둘 다 {@code ref_type='FX_TX'}, {@code ref_id=fx_transactions.id}.
 * 멱등키는 fxTxId 파생키({@code FX:{id}:OUT} / {@code :IN})로 재호출 시 중복 줄을 막는다(DECISIONS D4-3).
 *
 * <p>호출자({@code ExchangeSettleService})의 {@code @Transactional} 안에서 실행되어 fx 기록·잔액 갱신이
 * 하나의 DB B 로컬 트랜잭션으로 커밋·롤백된다. 잔액 부족은 입금 전 출금 레그에서 막혀
 * 부분 반영이 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CmaFundsAdapter implements CmaFundsPort {

    private static final String TX_FX_OUT = "FX_OUT";
    private static final String TX_FX_IN = "FX_IN";
    private static final String SOURCE_SYSTEM = "SYSTEM";   // 환전=시스템 내부 통화 스왑(수집 출처 아님)
    private static final String REF_TYPE_FX = "FX_TX";

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaLedgerWriter ledgerWriter;

    @Override
    public FxLegResult applyFxLegs(Long userId,
                                   String fromCurrency, BigDecimal fromAmount,
                                   String toCurrency, BigDecimal toAmount,
                                   Long fxTransactionId) {
        // 입력 계약 강제 — fromAmount.negate()가 음수 입력 시 출금을 입금으로 둔갑시키거나,
        // fxTransactionId=null이면 멱등키가 "FX:null:..."로 충돌하는 것을 막는다.
        if (fxTransactionId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환전 거래 ID가 필요합니다.");
        }
        if (fromCurrency == null || toCurrency == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환전 통화가 필요합니다.");
        }
        if (fromAmount == null || fromAmount.signum() <= 0
                || toAmount == null || toAmount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환전 금액은 0보다 커야 합니다.");
        }

        CmaAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌가 없어 환전을 반영할 수 없습니다.");
        }
        Long cmaAccountId = account.getId();

        // 출금 레그 먼저 — 잔액 부족이면 입금 전에 실패해 호출자 트랜잭션 전체가 롤백된다.
        BigDecimal remainFrom = ledgerWriter.applyEntry(
                userId, cmaAccountId, fromCurrency, TX_FX_OUT, SOURCE_SYSTEM, fromAmount.negate(),
                REF_TYPE_FX, fxTransactionId, "FX:" + fxTransactionId + ":OUT");

        BigDecimal remainTo = ledgerWriter.applyEntry(
                userId, cmaAccountId, toCurrency, TX_FX_IN, SOURCE_SYSTEM, toAmount,
                REF_TYPE_FX, fxTransactionId, "FX:" + fxTransactionId + ":IN");

        return new FxLegResult(remainFrom, remainTo);
    }

    @Override
    public BigDecimal poolBalance(Long userId, String currency) {
        CmaAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌가 없어 잔액을 조회할 수 없습니다.");
        }
        CmaBalance balance = balanceMapper.findByAccountIdAndCurrency(account.getId(), currency);
        return balance == null ? BigDecimal.ZERO : balance.getBalance();
    }
}
