package com.pocketstock.ledger.exchange.service;

import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.exchange.ExchangeRatePolicy;
import com.pocketstock.ledger.exchange.config.ExchangeProperties;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import com.pocketstock.ledger.exchange.dto.response.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 환율 조회 서비스 — {@link CurrencyRateProvider}(캐시 우선·야후 폴백)의 매매기준율에
 * {@link ExchangeRatePolicy}를 적용해 매수/매도 적용환율까지 포함한 응답을 만든다.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String USD = "USD";
    private static final String KRW = "KRW";

    private final CurrencyRateProvider rateProvider;
    private final ExchangeRatePolicy ratePolicy;
    private final ExchangeProperties props;

    /** USD/KRW 현재 환율 + 매수/매도 적용환율. 캐시·폴백 모두 비면 502. */
    public ExchangeRateResponse getUsdKrwRate() {
        CurrencyRateResponse latest = rateProvider.current();
        BigDecimal base = latest.exchangeRate();
        return new ExchangeRateResponse(
                USD, KRW,
                base,
                ratePolicy.buyRate(USD, base),
                ratePolicy.sellRate(USD, base),
                props.getPreferentialRate(),
                latest.change(),
                latest.updatedAt());
    }
}
