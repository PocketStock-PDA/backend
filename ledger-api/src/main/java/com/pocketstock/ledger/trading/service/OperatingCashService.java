package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.trading.domain.OperatingCashTransaction;
import com.pocketstock.ledger.trading.mapper.OperatingCashMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 회사 현금 처리(복식부기 상대계정, H1). 현재잔액은 {@code operating_cash_balances}(통화당 1행)에서
 * 원자 upsert하고, 거래 역사는 {@code operating_cash_transactions}(불변 journal)에 1줄씩 쌓는다.
 * 유저 예수금({@link DepositService})과 대칭이되 음수 가드만 없다(회사 순현금은 음수 가능).
 */
@Service
@RequiredArgsConstructor
public class OperatingCashService {

    private final OperatingCashMapper operatingCashMapper;

    /**
     * 회사 현금 1건 반영 — 잔액 원자 upsert 후 역사 1줄 append, 갱신된 잔액 반환.
     * 유저 예수금 leg의 반대 부호로 호출한다(매수 시 회사 +total, 매도 시 회사 −total).
     * @param signedAmount +수취(BUY) / −지급(SELL)
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
