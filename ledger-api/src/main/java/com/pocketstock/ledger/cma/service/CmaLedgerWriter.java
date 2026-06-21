package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.domain.CmaTransaction;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * CMA 원장 단일 레그 쓰기 공통 컴포넌트 (D4).
 *
 * <p>{@code cma_transactions} append + {@code cma_balances} 잔액 갱신을 한 곳에 모아
 * 원장 규칙(append-only · 잠금 · 멱등 · 출금가드)을 통일한다. 잔돈 수집(collect) 입금 1줄,
 * 환전({@code CmaFundsAdapter}) 출금/입금 2줄이 모두 이 메서드를 통한다.
 *
 * <p><b>트랜잭션 경계는 호출자 소유.</b> 이 컴포넌트는 자체 {@code @Transactional}을 열지 않고,
 * 호출자(예: {@code ExchangeSettleService}, collect 서비스)의 트랜잭션에 합류한다 —
 * 그래야 {@code FOR UPDATE} 잠금과 append/잔액갱신이 한 단위로 커밋·롤백된다.
 *
 * <p><b>부호 규약(전역):</b> {@code signedAmount}는 입금 +, 출금 −. 항상
 * {@code balance_after = 직전잔액 + signedAmount}가 성립한다. 정정(REVERT)은 원거래의 반대부호를
 * 같은 규약으로 넣는다(DECISIONS E-4).
 */
@Component
@RequiredArgsConstructor
public class CmaLedgerWriter {

    /** 통화별 표시용 이율 — 지갑 lazy 생성 시 기록(별도 수익률 정책 없음, 잔액 화면 기준값). */
    private static final BigDecimal KRW_INTEREST_RATE = new BigDecimal("0.0350");  // 연 3.5% (CMA 개설과 동일)
    private static final BigDecimal USD_INTEREST_RATE = new BigDecimal("0.0420");  // 연 4.2% (USD RP 표시값)
    private static final String USD = "USD";

    private final CmaBalanceMapper balanceMapper;
    private final CmaTransactionMapper transactionMapper;

    /**
     * 단일 레그 원장 기록 + 잔액 반영. 호출자 트랜잭션 안에서 호출한다.
     *
     * @param signedAmount 입금 +, 출금 − (0은 허용하지 않음)
     * @param idempotencyKey 멱등키. 동일 키 재호출 시 잔액을 다시 손대지 않고 기존 {@code balance_after} 반환.
     *                       {@code null}이면 멱등 보장 없이 항상 신규 기록(테스트·서버 자동생성 경로).
     * @return 반영 후 잔액 (balance_after)
     * @throws BusinessException 출금으로 잔액이 음수가 되면 {@code INSUFFICIENT_BALANCE}
     */
    public BigDecimal applyEntry(Long userId, Long cmaAccountId, String currency,
                                 String txType, String sourceType, BigDecimal signedAmount,
                                 String refType, Long refId, String idempotencyKey) {

        // 0) 금액 계약 강제 — 0/null 레그는 무의미한 원장행이므로 거부(null이면 잔액 계산 시 NPE).
        if (signedAmount == null || signedAmount.signum() == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "원장 기록 금액은 0이 될 수 없습니다.");
        }

        // 1) 멱등 선행 조회 — 이미 기록된 키면 잔액/잠금 손대지 않고 기존 결과 반환(불필요한 FOR UPDATE 회피)
        if (idempotencyKey != null) {
            CmaTransaction existing = transactionMapper.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                return existing.getBalanceAfter();
            }
        }

        // 2) 통화 지갑 lazy 생성(없으면 0원) 후 잠금 읽기. KRW는 개설 시 시드돼 있고, USD는 첫 입금 때 생성됨.
        //    insertBalance = INSERT IGNORE 라 기존 잔액은 보존되고, 이어진 FOR UPDATE가 반드시 행을 잠근다.
        balanceMapper.insertBalance(newWallet(cmaAccountId, currency));
        CmaBalance balance = balanceMapper.findByAccountIdAndCurrencyForUpdate(cmaAccountId, currency);

        // 3) 잔액 계산 + 출금 가드(입금은 통과)
        BigDecimal balanceAfter = balance.getBalance().add(signedAmount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    currency + " 잔액이 부족합니다. (보유 " + balance.getBalance()
                            + ", 필요 " + signedAmount.negate() + ")");
        }

        // 4) 원장 append. UNIQUE(idempotency_key)가 멱등 백스톱 — 1)~2) 사이 경합으로 다른 트랜잭션이
        //    먼저 기록했다면 insert가 신규 1건을 만들지 못한다(affected != 1) → 잔액 손대지 않고 기존값 반환.
        CmaTransaction tx = new CmaTransaction();
        tx.setUserId(userId);
        tx.setCmaAccountId(cmaAccountId);
        tx.setCurrency(currency);
        tx.setTxType(txType);
        tx.setSourceType(sourceType);
        tx.setAmount(signedAmount);
        tx.setBalanceAfter(balanceAfter);
        tx.setRefType(refType);
        tx.setRefId(refId);
        tx.setIdempotencyKey(idempotencyKey);
        int affected = transactionMapper.insert(tx);
        if (affected != 1) {
            CmaTransaction existing = transactionMapper.findByIdempotencyKey(idempotencyKey);
            return existing != null ? existing.getBalanceAfter() : balanceAfter;
        }

        // 5) 잔액 갱신 — 읽어 둔 interest_rate를 그대로 유지(덮어쓰기 아님)
        balance.setBalance(balanceAfter);
        balanceMapper.upsertBalance(balance);
        return balanceAfter;
    }

    private CmaBalance newWallet(Long cmaAccountId, String currency) {
        CmaBalance wallet = new CmaBalance();
        wallet.setCmaAccountId(cmaAccountId);
        wallet.setCurrency(currency);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setInterestRate(USD.equals(currency) ? USD_INTEREST_RATE : KRW_INTEREST_RATE);
        return wallet;
    }
}
