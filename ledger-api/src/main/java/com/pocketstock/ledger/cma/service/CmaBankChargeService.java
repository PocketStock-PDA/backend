package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.SourceDeduction;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaTransaction;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import com.pocketstock.ledger.cma.port.CmaChargePort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 은행계좌 → CMA 원화풀 충전 핵심 로직 ({@link CmaChargePort} 구현).
 *
 * <p>"은행에서 정확한 금액을 CMA 원화풀로 옮긴다"는 단일 책임만 가진다 — 거래 인증(txn-auth)도, 부족분 계산도 하지 않는다.
 * 사용자 직접 충전({@link CmaDepositService})과 trading의 매수 자동충전이 <b>이 한 곳을 공유</b>해 원장 규칙
 * (append-only · 멱등 · 소유/잔액 검증 · 교차 DB 차감 순서)이 갈리지 않게 한다.
 *
 * <p>한 트랜잭션 안에서: ① CMA 원화풀 입금({@link CmaLedgerWriter}, DEPOSIT 양수) →
 * ② 은행계좌 잔액 차감({@link AssetFeignClient#deductAccountBalances}, core-api/DB A). 잔돈 수집과 동형으로
 * 원장 기록을 먼저 하고 교차 DB 차감을 뒤에 호출한다 — 차감이 실패하면 트랜잭션이 롤백돼 부분 반영이 남지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CmaBankChargeService implements CmaChargePort {

    private static final String KRW = "KRW";
    private static final String TX_DEPOSIT = "DEPOSIT";   // 충전 입금(+)
    private static final String SOURCE_MANUAL = "MANUAL"; // 사용자/시스템 충전(수집 출처 아님)
    private static final String REF_BANK_ACCOUNT = "LINKED_BANK_ACCOUNT";

    private final CmaAccountMapper accountMapper;
    private final CmaTransactionMapper transactionMapper;
    private final CmaLedgerWriter ledgerWriter;
    private final AssetFeignClient assetFeignClient;

    @Override
    @Transactional
    public ChargeResult charge(Long userId, Long sourceAccountId, BigDecimal amount, String idempotencyKey) {
        requireUser(userId);
        String key = requireKey(idempotencyKey);
        Long source = requireSourceAccount(sourceAccountId);
        BigDecimal chargeAmount = requirePositive(amount);

        CmaAccount cma = accountMapper.findByUserId(userId);
        if (cma == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌가 없어 충전할 수 없습니다.");
        }

        // 멱등 재요청: 같은 키 충전이 이미 있으면 재충전 없이 기존 결과를 돌려준다.
        CmaTransaction existing = transactionMapper.findByIdempotencyKey(key);
        if (existing != null) {
            requireOwner(existing, userId);
            requireSameOperation(existing);   // 같은 키가 다른 용도 거래면 충전 결과로 오인 금지
            return new ChargeResult(existing.getAmount(), existing.getBalanceAfter());
        }

        // 은행계좌 소유·미해지 확인(타인/해지 계좌면 결과 비어 거부) + 잔액 부족 거부.
        LinkedAccountSummary src = findOwnedAccount(userId, source);
        if (src.balance() == null || src.balance().compareTo(chargeAmount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "출처 계좌 잔액이 부족합니다. (보유 "
                            + (src.balance() == null ? BigDecimal.ZERO : src.balance())
                            + ", 필요 " + chargeAmount + ")");
        }

        try {
            // ① CMA 원화풀 입금 — DEPOSIT/MANUAL, ref는 충전 재원 계좌(추적용).
            BigDecimal cmaAfter = ledgerWriter.applyEntry(userId, cma.getId(), KRW,
                    TX_DEPOSIT, SOURCE_MANUAL, chargeAmount, REF_BANK_ACCOUNT, source, key);
            // ② 은행계좌에서 차감(core-api, DB A) — 교차 DB라 원장 후 호출(실패 시 호출자 트랜잭션 롤백).
            assetFeignClient.deductAccountBalances(userId, List.of(new SourceDeduction(source, chargeAmount)));
            return new ChargeResult(chargeAmount, cmaAfter);
        } catch (DuplicateKeyException e) {
            // 거의 동시에 같은 키 2건이 멱등 단락을 통과한 경합 — UNIQUE가 두 번째를 막아 롤백. 재요청 권장.
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 충전입니다.");
        }
    }

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

    private String requireKey(String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "멱등키(idempotencyKey)가 필요합니다.");
        }
        return key;
    }

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "충전 금액은 0보다 커야 합니다.");
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

    /**
     * 멱등 replay 대상이 정말 같은 의미의 충전(DEPOSIT/MANUAL/KRW)인지 확인 — 같은 키가 다른 용도
     * 거래(예: 수집 COLLECT)에 쓰인 경우 그 행을 충전 결과로 오인해 돌려주지 않도록 거부한다.
     */
    private void requireSameOperation(CmaTransaction tx) {
        if (!TX_DEPOSIT.equals(tx.getTxType())
                || !SOURCE_MANUAL.equals(tx.getSourceType())
                || !KRW.equals(tx.getCurrency())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 다른 용도로 사용된 멱등키입니다.");
        }
    }
}
