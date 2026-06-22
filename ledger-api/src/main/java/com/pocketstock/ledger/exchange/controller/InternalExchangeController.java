package com.pocketstock.ledger.exchange.controller;

import com.pocketstock.ledger.exchange.dto.response.UsdKrwRateView;
import com.pocketstock.ledger.exchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * core→ledger 내부 호출 전용(읽기). 잔돈 스캔에서 외화 잔돈 KRW 환산용 매매기준율을 가져간다.
 * 환율 캐시 콜드스타트 시 ExchangeRateService가 던지는 502(EXTERNAL_API_ERROR)가 그대로 전파된다.
 */
@RestController
@RequestMapping("/internal/exchange")
@RequiredArgsConstructor
public class InternalExchangeController {

    private final ExchangeRateService rateService;

    @GetMapping("/usd-krw-rate")
    public UsdKrwRateView getUsdKrwRate() {
        return new UsdKrwRateView(rateService.getUsdKrwRate().baseRate());
    }
}
