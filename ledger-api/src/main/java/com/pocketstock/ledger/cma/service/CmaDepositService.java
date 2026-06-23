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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 은행계좌 → CMA 원화풀 부족분 충전(DEPOSIT) 체결.
 *
 * <p>매수 화면에서 보낸 목표 금액({@code targetAmount})과 현재 CMA 원화풀 잔액의 차액(부족분)만
 * 연동 은행계좌에서 끌어와 CMA 원화풀에 입금한다. 이미 충분하면 이체하지 않는다(KRW 전용 —
 * 해외/USD 충전은 매수 시점 자동환전이 담당).
 *
 * <p>한 트랜잭션 안에서: ① CMA 원화풀 입금({@link CmaLedgerWriter}, DEPOSIT 양수) →
 * ② 은행계좌 잔액 차감({@link AssetFeignClient#deductAccountBalances}). 잔돈 수집(collect)과
 * 동형으로 원장 기록을 먼저 하고 교차 DB 차감을 뒤에 호출한다 — 차감이 실패하면 본 트랜잭션이
 * 롤백돼 부분 반영(원장만 늘고 은행은 그대로)이 남지 않는다.
 *
 * <p>은행 잔액 부족은 원장 기록 전에 선검증해 거부한다(차감 SQL의 {@code balance >= amount} 가드가 백스톱).
 * 거래 인증은 사전 txn-auth 세션({@link TxnAuthGuard})으로 처리하고, 멱등키로 따닥 탭·재전송 시 이중 충전을 막는다.
 */
@Service
@RequiredArgsConstructor
public class CmaDepositService {

    private static final String KRW = "KRW";
    private static final String TX_DEPOSIT = "DEPOSIT";   // 수동 충전 입금(+)
    private static final String SOURCE_MANUAL = "MANUAL"; // 사용자 트리거(수집 출처 아님)
    private static final String REF_BANK_ACCOUNT = "LINKED_BANK_ACCOUNT";

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaTransactionMapper transactionMapper;
    private final CmaLedgerWriter ledgerWriter;
    private final AssetFeignClient assetFeignClient;
    private final TxnAuthGuard txnAuthGuard;

    @Transactional
    public CmaDepositResponse deposit(Long userId, CmaDepositRequest req) {
        requireUser(userId);
        String key = requireKey(req.idempotencyKey());
        BigDecimal targetAmount = requirePositive(req.targetAmount());
        Long sourceAccountId = requireSourceAccount(req.sourceAccountId());

        CmaAccount cma = accountMapper.findByUserId(userId);
        if (cma == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌가 없어 충전할 수 없습니다.");
        }

        // 멱등 재요청: 같은 키 충전이 이미 있으면 재적용 없이 기존 결과를 돌려준다(BUY_TRANSFER와 동형).
        CmaTransaction existing = transactionMapper.findByIdempotencyKey(key);
        if (existing != null) {
            requireOwner(existing, userId);
            return CmaDepositResponse.charged(targetAmount, existing.getAmount(), existing.getBalanceAfter());
        }

        txnAuthGuard.requireTxnAuth(userId);

        // 부족분 = 목표 − 현재 CMA 원화풀 잔액. 0 이하면 이미 충분 → 이체하지 않는다.
        BigDecimal cmaBalance = poolBalance(cma.getId(), KRW);
        BigDecimal shortfall = targetAmount.subtract(cmaBalance);
        if (shortfall.signum() <= 0) {
            return CmaDepositResponse.sufficient(targetAmount, cmaBalance);
        }

        // 은행계좌 소유·상태 확인(해지·타인 계좌면 결과 비어 거부) + 잔액 부족 거부.
        LinkedAccountSummary source = findOwnedAccount(userId, sourceAccountId);
        if (source.balance() == null || source.balance().compareTo(shortfall) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "출처 계좌 잔액이 부족합니다. (보유 "
                            + (source.balance() == null ? BigDecimal.ZERO : source.balance())
                            + ", 필요 " + shortfall + ")");
        }

        try {
            // ① CMA 원화풀 입금 — DEPOSIT/MANUAL, ref는 충전 재원 계좌(추적용).
            BigDecimal cmaAfter = ledgerWriter.applyEntry(userId, cma.getId(), KRW,
                    TX_DEPOSIT, SOURCE_MANUAL, shortfall, REF_BANK_ACCOUNT, sourceAccountId, key);
            // ② 은행계좌에서 부족분 차감(core-api, DB A) — 교차 DB라 원장 후 호출(실패 시 본 트랜잭션 롤백).
            assetFeignClient.deductAccountBalances(userId, List.of(new SourceDeduction(sourceAccountId, shortfall)));
            return CmaDepositResponse.charged(targetAmount, shortfall, cmaAfter);
        } catch (DuplicateKeyException e) {
            // 거의 동시에 같은 키 2건이 멱등 단락을 통과한 경합 — UNIQUE가 두 번째를 막아 롤백. 재요청 권장.
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 충전입니다.");
        }
    }

    /** 현재 CMA 통화풀 잔액(없으면 0). */
    private BigDecimal poolBalance(Long cmaAccountId, String currency) {
        CmaBalance balance = balanceMapper.findByAccountIdAndCurrency(cmaAccountId, currency);
        return balance == null ? BigDecimal.ZERO : balance.getBalance();
    }

    /** 본인 소유 + 미해지 연동 계좌만 반환(SQL이 user_id·closed_at 필터). 없으면 거부. */
    private LinkedAccountSummary findOwnedAccount(Long userId, Long sourceAccountId) {
        List<LinkedAccountSummary> accounts = assetFeignClient.getLinkedAccounts(userId, List.of(sourceAccountId));
        if (accounts.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "충전 재원으로 쓸 수 없는 계좌입니다.");
        }
        return accounts.get(0);
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

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "목표 금액은 0보다 커야 합니다.");
        }
        return amount;
    }

    private Long requireSourceAccount(Long sourceAccountId) {
        if (sourceAccountId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출처 계좌(sourceAccountId)가 필요합니다.");
        }
        return sourceAccountId;
    }

    /** 멱등키는 전역 UNIQUE — 다른 유저 키와 충돌하면 남의 거래 노출 금지(409). */
    private void requireOwner(CmaTransaction tx, Long userId) {
        if (!userId.equals(tx.getUserId())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 사용된 멱등키입니다.");
        }
    }
}
