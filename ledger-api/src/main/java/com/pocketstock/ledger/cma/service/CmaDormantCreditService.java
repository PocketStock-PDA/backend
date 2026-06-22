package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.dto.request.InternalCmaCreditRequest;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 휴면계좌 해지 → CMA 입금(core→ledger Feign write, F-D).
 *
 * <p>core(DB A)가 휴면 은행 계좌를 소프트 해지하며 그 잔액을 이 서비스로 넘기면, 사용자 CMA 풀에
 * {@code txType=DORMANT}(+)로 입금한다. {@link CmaLedgerWriter}로 단일 레그(append + 잔액)를 한 트랜잭션에
 * 기록한다. 멱등키 {@code DORMANT:{accountId}}를 ledger가 강제 — core의 DB A 해지가 실패해 재시도되어도
 * 같은 계좌 입금이 한 번만 반영된다(CMA collect 카드 mark와 동일한 "DB B 입금 → DB A 해지" 순서).
 *
 * <p>{@code source_type}은 수집 출처(ACCOUNT/CARD/POINT)가 아닌 내부 자금이동이므로 {@code SYSTEM}
 * (자금이동 BUY_TRANSFER과 동일 어휘), {@code ref_type}은 출처 테이블 {@code LINKED_BANK_ACCOUNT}(E-2).
 */
@Service
@RequiredArgsConstructor
public class CmaDormantCreditService {

    private static final String TX_DORMANT = "DORMANT";
    private static final String SOURCE_SYSTEM = "SYSTEM";
    private static final String REF_BANK_ACCOUNT = "LINKED_BANK_ACCOUNT";

    private final CmaAccountMapper accountMapper;
    private final CmaLedgerWriter ledgerWriter;

    /** @return 입금 반영 후 CMA 통화풀 잔액(balance_after). */
    @Transactional
    public BigDecimal credit(InternalCmaCreditRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 본문이 필요합니다.");
        }
        if (req.userId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId가 필요합니다.");
        }
        if (req.accountId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "accountId가 필요합니다.");
        }
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "입금 금액은 0보다 커야 합니다.");
        }
        String currency = req.currency() == null ? "" : req.currency().trim();
        if (!"KRW".equals(currency) && !"USD".equals(currency)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "통화는 KRW 또는 USD여야 합니다.");
        }

        CmaAccount cma = accountMapper.findByUserId(req.userId());
        if (cma == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌가 없어 휴면 잔액을 입금할 수 없습니다.");
        }

        String idempotencyKey = TX_DORMANT + ":" + req.accountId();
        return ledgerWriter.applyEntry(req.userId(), cma.getId(), currency,
                TX_DORMANT, SOURCE_SYSTEM, req.amount(),
                REF_BANK_ACCOUNT, req.accountId(), idempotencyKey);
    }
}
