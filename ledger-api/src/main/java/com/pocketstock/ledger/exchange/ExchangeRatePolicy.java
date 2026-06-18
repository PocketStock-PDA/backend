package com.pocketstock.ledger.exchange;

import com.pocketstock.ledger.exchange.config.ExchangeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 적용환율 산정 — 매매기준율에 스프레드·우대율을 반영해 매수/매도 환율을 만든다.
 *
 * <pre>
 *   실효 스프레드 = s × (1 − p)              (s=전신환 스프레드, p=우대율)
 *   매수(고객이 외화 살 때, KRW→USD) = 기준율 × (1 + 실효스프레드)
 *   매도(고객이 외화 팔 때, USD→KRW) = 기준율 × (1 − 실효스프레드)
 * </pre>
 *
 * 비용은 환율에 내재(별도 수수료 없음). 예) 기준 1535.40·s=0.96%·p=90%
 * → 실효 0.096% → 매수 1536.87 / 매도 1533.93. (근거 backend#54)
 */
@Component
@RequiredArgsConstructor
public class ExchangeRatePolicy {

    /** 환율은 원/외화로 표기 — 소수 2자리(예 1536.87). */
    private static final int RATE_SCALE = 2;

    private final ExchangeProperties props;

    /** 고객이 해당 외화를 살 때(KRW→외화) 적용환율. */
    public BigDecimal buyRate(String currency, BigDecimal baseRate) {
        return baseRate.multiply(BigDecimal.ONE.add(effectiveSpread(currency)))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 고객이 해당 외화를 팔 때(외화→KRW) 적용환율. */
    public BigDecimal sellRate(String currency, BigDecimal baseRate) {
        return baseRate.multiply(BigDecimal.ONE.subtract(effectiveSpread(currency)))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** s × (1 − p). 통화별 스프레드 미설정 시 0(=기준율 그대로). */
    public BigDecimal effectiveSpread(String currency) {
        BigDecimal s = props.getSpread() == null
                ? BigDecimal.ZERO
                : props.getSpread().getOrDefault(currency, BigDecimal.ZERO);
        BigDecimal p = props.getPreferentialRate() == null
                ? BigDecimal.ZERO
                : props.getPreferentialRate();
        return s.multiply(BigDecimal.ONE.subtract(p));
    }
}
