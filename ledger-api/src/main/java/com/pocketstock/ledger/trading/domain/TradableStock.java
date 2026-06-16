package com.pocketstock.ledger.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 거래 가능 종목 마스터 (tradable_stocks, DB B 원장).
 * 한투 .mst/.COD 정제 → 시드 적재. stock_code = 단축코드(KR 6자리)/심볼(US AAPL) = LS API tr_key.
 * market = KOSPI | KOSDAQ | NASDAQ | NYSE | AMEX.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradableStock {

    private Long id;
    private String stockCode;       // 단축코드(KR 6자리)/심볼(US) — 주문·표시·LS tr_key
    private String market;          // KOSPI | KOSDAQ | NASDAQ | NYSE | AMEX
    private String standardCode;    // 표준코드/ISIN (KR)
    private String stockName;       // 한글종목명
    private String englishName;     // 영문명(US)
    private String rtSymbol;        // 실시간 시세 구독 심볼(null이면 stockCode 사용)
    private String currency;        // KRW | USD
    private String secType;         // STOCK | ETF
    private Boolean isFractional;
    private Boolean isActive;
    private String logoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
