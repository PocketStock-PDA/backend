package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.trading.port.CmaPoolPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link CmaPoolPort} 실 어댑터 (CMA 도메인) — 주문 자동충당/환류의 <b>CMA풀 leg</b>를 실제 원장으로 처리한다.
 *
 * <p>매수 충당은 BUY_TRANSFER(−), 매도 환류는 SELL_RETURN(+)로 {@link CmaLedgerWriter}에 한 줄씩 기록한다
 * (ref_type='ORDER', ref_id=orders.id). 잔액 갱신·출금가드·멱등(idempotency_key UNIQUE)은 모두 ledger writer가 소유한다.
 *
 * <p>호출자({@code OrderFundingService})의 {@code @Transactional} 안에서 실행되어 예수금 leg과 한 단위로
 * 커밋·롤백된다. 충당 시 잔액 부족은 출금 leg에서 막혀 부분 반영이 남지 않는다.
 * 수동 이체({@code CmaTransferService})와 달리 거래 인증은 호출자가 주문 진입에서 1회 처리한다.
 */
@Component
@RequiredArgsConstructor
public class CmaPoolAdapter implements CmaPoolPort {

    private static final String TX_BUY_TRANSFER = "BUY_TRANSFER";   // 매수 충당: CMA풀 출금(−)
    private static final String TX_SELL_RETURN = "SELL_RETURN";     // 매도 환류: CMA풀 입금(+)
    private static final String TX_REVERT = "REVERT";              // 충당 반납(취소·가격개선): CMA풀 입금(+), BUY_TRANSFER 보상
    private static final String TX_DIVIDEND = "DIVIDEND";          // 배당 지급: CMA풀 입금(+), 주문 무관 외부 인플로우
    private static final String SOURCE_SYSTEM = "SYSTEM";           // 주문 연계 시스템 이동(수집 출처 아님)
    private static final String SOURCE_DIVIDEND = "DIVIDEND";       // 배당 인플로우 출처
    private static final String REF_TYPE_ORDER = "ORDER";
    private static final String REF_TYPE_DIVIDEND = "DIVIDEND";

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaLedgerWriter ledgerWriter;

    @Override
    public BigDecimal withdrawForBuy(Long userId, String currency, BigDecimal amount,
                                     Long orderId, String idempotencyKey) {
        Long cmaAccountId = requireAccount(userId, currency, amount);
        // 출금 leg — 잔액 부족이면 INSUFFICIENT_BALANCE로 막혀 호출자 트랜잭션이 롤백된다.
        return ledgerWriter.applyEntry(userId, cmaAccountId, currency,
                TX_BUY_TRANSFER, SOURCE_SYSTEM, amount.negate(), REF_TYPE_ORDER, orderId, idempotencyKey);
    }

    @Override
    public BigDecimal depositFromSell(Long userId, String currency, BigDecimal amount,
                                      Long orderId, String idempotencyKey) {
        Long cmaAccountId = requireAccount(userId, currency, amount);
        return ledgerWriter.applyEntry(userId, cmaAccountId, currency,
                TX_SELL_RETURN, SOURCE_SYSTEM, amount, REF_TYPE_ORDER, orderId, idempotencyKey);
    }

    @Override
    public BigDecimal revertBuyTransfer(Long userId, String currency, BigDecimal amount,
                                        Long orderId, String idempotencyKey) {
        Long cmaAccountId = requireAccount(userId, currency, amount);
        return ledgerWriter.applyEntry(userId, cmaAccountId, currency,
                TX_REVERT, SOURCE_SYSTEM, amount, REF_TYPE_ORDER, orderId, idempotencyKey);
    }

    @Override
    public BigDecimal creditDividend(Long userId, String currency, BigDecimal amount,
                                     Long payoutId, String idempotencyKey) {
        Long cmaAccountId = requireAccount(userId, currency, amount);
        return ledgerWriter.applyEntry(userId, cmaAccountId, currency,
                TX_DIVIDEND, SOURCE_DIVIDEND, amount, REF_TYPE_DIVIDEND, payoutId, idempotencyKey);
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

    /** 금액 계약 강제 + CMA 계좌 해석 — 음수 금액이 부호를 뒤집거나 계좌 없이 원장이 쓰이는 것을 막는다. */
    private Long requireAccount(Long userId, String currency, BigDecimal amount) {
        if (currency == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "통화가 필요합니다.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자금이동 금액은 0보다 커야 합니다.");
        }
        CmaAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌가 없어 자금을 이동할 수 없습니다.");
        }
        return account.getId();
    }
}
