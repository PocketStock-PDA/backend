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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 사용자가 직접 누르는 매수 부족분 충전(DEPOSIT) — {@code POST /api/cma/deposit}.
 *
 * <p>매수 화면에서 사려는 금액({@code targetAmount})을 보내면 현재 CMA 원화풀 잔액과의 차액(부족분)만
 * 은행계좌에서 끌어와 충전한다. 이미 충분하면 이체하지 않는다(KRW 전용 — 해외/USD 충전은 매수 시점 자동환전이 담당).
 *
 * <p>이 서비스는 <b>사용자 직접 충전 전용 껍데기</b>다 — 거래 인증(txn-auth)과 부족분 계산만 책임지고,
 * 실제 은행 → CMA 이동은 공용 {@link CmaChargePort}({@link CmaBankChargeService})에 위임한다.
 * trading의 매수 자동충전도 같은 포트를 호출하므로 원장 이동 규칙이 한 곳에서 관리된다.
 */
@Service
@RequiredArgsConstructor
public class CmaDepositService {

    private static final String KRW = "KRW";

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaTransactionMapper transactionMapper;
    private final CmaChargePort chargePort;
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

        // 멱등 재요청: 같은 키 충전이 이미 있으면 재적용 없이 기존 결과를 돌려준다(txn-auth·충전 안 탐).
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

        // 실제 은행 → CMA 이동은 공용 포트에 위임(소유/잔액 검증·원장 입금·은행 차감은 거기서).
        CmaChargePort.ChargeResult result = chargePort.charge(userId, sourceAccountId, shortfall, key);
        return CmaDepositResponse.charged(targetAmount, result.chargedAmount(), result.cmaBalanceAfter());
    }

    /** 현재 CMA 통화풀 잔액(없으면 0). */
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
