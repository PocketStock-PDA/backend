package com.pocketstock.ledger.firm.service;

import com.pocketstock.ledger.firm.domain.OperatingCashTransaction;
import com.pocketstock.ledger.firm.mapper.OperatingCashMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 회사 현금 처리(복식부기 상대계정). 현재잔액은 {@code operating_cash_balances}(통화당 1행)에서
 * 원자 upsert하고, 거래 역사는 {@code operating_cash_transactions}(불변 journal)에 1줄씩 쌓는다.
 * 유저 예수금({@code DepositService})과 대칭이되 음수 가드만 없다(회사 순현금은 음수 가능).
 *
 * <p>전사(firm) 장부 — trading 매매 현금 leg(H1)·exchange 환전 통화풀 leg(H5)이 함께 호출한다.
 * 어느 도메인 전용도 아니라 {@code ledger.firm}에 둔다(형제 도메인 간 의존 회피).
 */
@Service
@RequiredArgsConstructor
public class OperatingCashService {

    private final OperatingCashMapper operatingCashMapper;

    /**
     * 회사 현금 1건 반영 — 잔액 원자 upsert 후 역사 1줄 append, 갱신된 잔액 반환.
     * 유저 예수금 leg의 반대 부호로 호출한다(매수 시 회사 +total, 매도 시 회사 −total).
     * @param signedAmount +수취 / −지급
     * @param idempotencyKey 같은 키 재적재는 UNIQUE로 차단(결정적 키, 예: order:{orderId}). null 허용.
     */
    @Transactional
    public BigDecimal record(String txType, BigDecimal signedAmount, String currency,
                             String refType, Long refId, String idempotencyKey) {
        // ① 잔액 원자 upsert(통화행 self-seeding). 음수 가드 없음 — 회사 순현금은 음수 가능.
        operatingCashMapper.applyDelta(currency, signedAmount);
        // ② 갱신된 잔액을 박제해 불변 역사 1줄 append (같은 트랜잭션 — 자기 UPDATE 결과를 읽음).
        BigDecimal after = operatingCashMapper.findBalanceByCurrency(currency);
        operatingCashMapper.insert(OperatingCashTransaction.builder()
                .currency(currency)
                .txType(txType)
                .amount(signedAmount)
                .balanceAfter(after)
                .refType(refType)
                .refId(refId)
                .idempotencyKey(idempotencyKey)
                .build());
        return after;
    }
}
