package com.pocketstock.ledger.exchange.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateCache;
import com.pocketstock.ledger.exchange.ExchangeRatePolicy;
import com.pocketstock.ledger.exchange.config.ExchangeProperties;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import com.pocketstock.ledger.exchange.dto.response.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 환율 조회 서비스 — 캐시(LS CUR SSOT)의 매매기준율에 {@link ExchangeRatePolicy}를
 * 적용해 매수/매도 적용환율까지 포함한 응답을 만든다.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String USD = "USD";
    private static final String KRW = "KRW";

    private final CurrencyRateCache rateCache;
    private final ExchangeRatePolicy ratePolicy;
    private final ExchangeProperties props;

    /** USD/KRW 현재 환율 + 매수/매도 적용환율. 콜드스타트(틱 미수신) 시 502. */
    public ExchangeRateResponse getUsdKrwRate() {
        CurrencyRateResponse latest = rateCache.get();
        if (latest == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환율 정보를 아직 받지 못했습니다.");
        }
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
