package com.pocketstock.ledger.exchange;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 환전 환산 — 매매기준율(mid) + 방향 → 적용환율·수령액. 검증({@code /validate})과
 * 체결({@code ExchangeSettleService})이 공유해 미리보기 금액 == 실제 체결 금액을 보장한다.
 *
 * <p>절사는 고객 불리 방향(DOWN): USD는 센트(2자리), KRW는 원(0자리).
 * 적용환율 산정 자체는 {@link ExchangeRatePolicy}(스프레드·우대 내재)에 위임.
 */
@Component
@RequiredArgsConstructor
public class FxQuoteCalculator {

    private static final String USD = "USD";
    private static final int USD_SCALE = 2;
    private static final int KRW_SCALE = 0;

    private final ExchangeRatePolicy ratePolicy;

    /** 방향에 맞는 적용환율(매수 or 매도) — 금액 없이 환율만 필요할 때. */
    public BigDecimal appliedRate(FxDirection direction, BigDecimal midRate) {
        return direction == FxDirection.KRW_TO_USD
                ? ratePolicy.buyRate(USD, midRate)
                : ratePolicy.sellRate(USD, midRate);
    }

    /** 적용환율 + 절사된 수령액. {@code amount}는 양수 가정(상위에서 검증). */
    public FxQuote quote(FxDirection direction, BigDecimal amount, BigDecimal midRate) {
        BigDecimal rate = appliedRate(direction, midRate);
        BigDecimal receive = direction == FxDirection.KRW_TO_USD
                ? amount.divide(rate, USD_SCALE, RoundingMode.DOWN)        // KRW ÷ 매수환율
                : amount.multiply(rate).setScale(KRW_SCALE, RoundingMode.DOWN);  // USD × 매도환율
        return new FxQuote(direction.from(), direction.to(), rate, receive);
    }
}
