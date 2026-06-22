package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.domain.CmaTransaction;
import com.pocketstock.ledger.cma.dto.request.CmaTransferRequest;
import com.pocketstock.ledger.cma.dto.response.CmaTransferResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import com.pocketstock.ledger.cma.port.DepositFundsPort;
import com.pocketstock.user.security.TxnAuthGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * CMA 풀 → 위탁 예수금 자금이동(BUY_TRANSFER) 체결.
 *
 * <p>한 트랜잭션 안에서: ① CMA 통화풀 차감({@link CmaLedgerWriter}, BUY_TRANSFER 음수) →
 * ② 위탁 예수금 입금({@link DepositFundsPort}, IN_TRANSFER 양수). cma·trading이 같은 DB B라
 * Saga 없이 로컬 트랜잭션 + 실패 시 롤백. 풀 차감을 먼저 해 잔액 부족이면 입금 전에 막아 부분반영을 남기지 않는다
 * (환전 {@code ExchangeSettleService}의 FX_OUT 선행과 동형).
 *
 * <p>market이 출금 통화풀과 입금 예수금을 함께 결정한다(DOMESTIC=KRW, OVERSEAS=USD).
 * 거래 인증은 사전 txn-auth 세션({@link TxnAuthGuard})으로 처리한다. 멱등키는 클라가 발급하며,
 * 두 레그가 공유 base에서 파생({@code TOPUP:{key}:CMA} / {@code :DEP})돼 재요청 시 이중 이체를 막는다.
 */
@Service
@RequiredArgsConstructor
public class CmaTransferService {

    private static final String TX_BUY_TRANSFER = "BUY_TRANSFER";   // CMA 풀 출금(−)
    private static final String SOURCE_SYSTEM = "SYSTEM";           // 내부 자금이동(수집 출처 아님)
    private static final String REF_TYPE = "DEPOSIT_TOPUP";

    /** 위탁 market → 통화풀/예수금 통화 (DOMESTIC=KRW / OVERSEAS=USD) */
    private static final Map<String, String> MARKET_CURRENCY = Map.of(
            "DOMESTIC", "KRW",
            "OVERSEAS", "USD");

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaTransactionMapper transactionMapper;
    private final CmaLedgerWriter ledgerWriter;
    private final DepositFundsPort depositFunds;
    private final TxnAuthGuard txnAuthGuard;

    @Transactional
    public CmaTransferResponse transfer(Long userId, CmaTransferRequest req) {
        requireUser(userId);
        String key = requireKey(req.idempotencyKey());
        String market = requireMarket(req.market());
        String currency = MARKET_CURRENCY.get(market);
        BigDecimal amount = requirePositive(req.amount());

        CmaAccount cma = accountMapper.findByUserId(userId);
        if (cma == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌가 없어 자금을 이체할 수 없습니다.");
        }

        String cmaKey = "TOPUP:" + key + ":CMA";
        String depKey = "TOPUP:" + key + ":DEP";

        // 멱등 재요청: 같은 키 이체가 이미 있으면 leg 재적용 없이 현재 잔액으로 결과를 돌려준다(환전과 동형).
        CmaTransaction existing = transactionMapper.findByIdempotencyKey(cmaKey);
        if (existing != null) {
            requireOwner(existing, userId);
            return new CmaTransferResponse(market, currency, existing.getAmount().negate(),
                    poolBalance(cma.getId(), currency), depositFunds.depositBalance(userId, market));
        }

        txnAuthGuard.requireTxnAuth(userId);
        try {
            // ① CMA 풀 차감 먼저 — 잔액 부족이면 입금 전에 INSUFFICIENT_BALANCE로 막혀 롤백된다.
            BigDecimal cmaAfter = ledgerWriter.applyEntry(userId, cma.getId(), currency,
                    TX_BUY_TRANSFER, SOURCE_SYSTEM, amount.negate(), REF_TYPE, null, cmaKey);
            // ② 위탁 예수금 입금 — 같은 트랜잭션에 합류(IN_TRANSFER).
            BigDecimal depositAfter = depositFunds.creditDeposit(userId, market, currency, amount, depKey);
            return new CmaTransferResponse(market, currency, amount, cmaAfter, depositAfter);
        } catch (DuplicateKeyException e) {
            // 거의 동시에 같은 키 2건이 위 단락을 통과한 경합 — UNIQUE가 두 번째를 막아 롤백. 재요청 권장.
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 자금 이체입니다.");
        }
    }

    /** 현재 CMA 통화풀 잔액(없으면 0) — 멱등 replay 응답용. */
    private BigDecimal poolBalance(Long cmaAccountId, String currency) {
        CmaBalance balance = balanceMapper.findByAccountIdAndCurrency(cmaAccountId, currency);
        return balance == null ? BigDecimal.ZERO : balance.getBalance();
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    /** 클라 발급 멱등키 필수 — 빈 값이면 거부(따닥 탭·재전송 방어가 무력해짐). */
    private String requireKey(String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "멱등키(idempotencyKey)가 필요합니다.");
        }
        return key;
    }

    /** market은 DOMESTIC|OVERSEAS만 허용 — 통화풀/예수금 통화를 결정하는 키. */
    private String requireMarket(String market) {
        if (market == null || !MARKET_CURRENCY.containsKey(market)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "market은 DOMESTIC 또는 OVERSEAS여야 합니다.");
        }
        return market;
    }

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이체 금액은 0보다 커야 합니다.");
        }
        return amount;
    }

    /** 멱등키는 전역 UNIQUE — 다른 유저 키와 충돌하면 남의 이체 노출 금지(409). */
    private void requireOwner(CmaTransaction tx, Long userId) {
        if (!userId.equals(tx.getUserId())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 사용된 멱등키입니다.");
        }
    }
}
