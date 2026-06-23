package com.pocketstock.ledger.exchange.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.exchange.FxDirection;
import com.pocketstock.ledger.exchange.FxQuoteCalculator;
import com.pocketstock.ledger.exchange.dto.response.ExchangeValidateResponse;
import com.pocketstock.ledger.exchange.dto.response.FxHistoryResponse;
import com.pocketstock.ledger.exchange.mapper.FxTransactionMapper;
import com.pocketstock.ledger.exchange.port.CmaFundsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 환전 조회 서비스 — 이력 페이징 + 체결 전 가능여부·가능금액 검증(읽기 전용 dry-run).
 */
@Service
@RequiredArgsConstructor
public class FxQueryService {

    private final FxTransactionMapper fxMapper;
    private final CurrencyRateProvider rateProvider;
    private final FxQuoteCalculator quoteCalc;
    private final CmaFundsPort cmaFunds;

    @Transactional(readOnly = true)
    public FxHistoryResponse getHistory(Long userId, int page, int size) {
        List<FxHistoryResponse.Item> items = fxMapper.findByUser(userId, page * size, size)
                .stream()
                .map(FxHistoryResponse.Item::from)
                .toList();
        long total = fxMapper.countByUser(userId);
        return new FxHistoryResponse(items, page, total);
    }

    /**
     * 환전 가능여부·가능금액 검증 — 원장·거래인증 없이 체결과 같은 환산({@link FxQuoteCalculator})으로
     * 미리보기. {@code amount} 없으면 환율·잔액·최대금액만, 있으면 그 금액까지 검증한다.
     *
     * <p>검증 순서: 환율 가용 → 금액 양수 → 잔액 충분 → 최소금액. 첫 위반에서 사유 확정.
     */
    @Transactional(readOnly = true)
    public ExchangeValidateResponse validate(Long userId, FxDirection direction, BigDecimal amount) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        // ① 환율 — 캐시·폴백 모두 비면 환산 자체가 불가(체결도 502 나는 상태).
        BigDecimal mid;
        try {
            mid = rateProvider.current().exchangeRate();
        } catch (BusinessException e) {
            return ExchangeValidateResponse.rateUnavailable(direction, amount);
        }

        BigDecimal appliedRate = quoteCalc.appliedRate(direction, mid);
        BigDecimal balance = cmaFunds.poolBalance(userId, direction.from());

        // amount 미지정: 환율·잔액·최대금액만. 잔액>0이면 환전 가능.
        if (amount == null) {
            boolean hasBalance = balance.signum() > 0;
            return build(direction, appliedRate, null, null, balance,
                    hasBalance ? null : ExchangeValidateResponse.INSUFFICIENT_BALANCE);
        }

        // ② 금액 양수
        if (amount.signum() <= 0) {
            return build(direction, appliedRate, amount, null, balance,
                    ExchangeValidateResponse.INVALID_AMOUNT);
        }

        BigDecimal receive = quoteCalc.quote(direction, amount, mid).receiveAmount();

        // ③ 잔액 충분
        if (amount.compareTo(balance) > 0) {
            return build(direction, appliedRate, amount, receive, balance,
                    ExchangeValidateResponse.INSUFFICIENT_BALANCE);
        }
        // ④ 최소금액 — 수령액이 1센트/1원 미만이면 환전 불가(0 수령 방지).
        if (receive.signum() <= 0) {
            return build(direction, appliedRate, amount, receive, balance,
                    ExchangeValidateResponse.BELOW_MINIMUM);
        }
        return build(direction, appliedRate, amount, receive, balance, null);
    }

    private ExchangeValidateResponse build(FxDirection d, BigDecimal appliedRate,
                                           BigDecimal inputAmount, BigDecimal expectedReceive,
                                           BigDecimal balance, String reason) {
        return new ExchangeValidateResponse(
                reason == null, d.name(), d.from(), d.to(),
                appliedRate, inputAmount, expectedReceive, balance, balance, reason);
    }
}
