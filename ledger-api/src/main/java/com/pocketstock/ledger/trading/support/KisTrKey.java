package com.pocketstock.ledger.trading.support;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.TradableStock;

/**
 * 미국 종목의 KIS 실시간 구독 키(tr_key) 조립 — vendor 전용 어댑터.
 * 세션(정규장/주간)에 따라 prefix(D/R)와 시장코드(NAS↔BAQ)가 달라지는 KIS 규약을
 * {@link MarketSession}과 종목의 {@code (exchange, stock_code)}로부터 한 곳에서 만든다.
 *
 * <p>시장코드는 {@link OverseasMarket}에서 파생한다(거래소→정규/주간 코드 단일 진실원).
 * <ul>
 *   <li>{@link MarketSession#REGULAR}: {@code "D" + 정규시장코드 + 심볼} → {@code DNASAAPL}</li>
 *   <li>{@link MarketSession#DAY}: {@code "R" + 주간시장코드 + 심볼} → {@code RBAQAAPL}</li>
 *   <li>{@link MarketSession#CLOSED}: 구독 불가 → {@code null}</li>
 * </ul>
 * KIS 문서 [해외주식] 실시간시세(HDFSASP0/HDFSCNT0) tr_key 규약 기준.
 */
public final class KisTrKey {

    private KisTrKey() {
    }

    /** 종목 마스터로부터 조립. */
    public static String of(MarketSession session, TradableStock stock) {
        return of(session, stock.getExchange(), stock.getStockCode());
    }

    /**
     * (세션 + 거래소 + 심볼) → KIS 구독 tr_key. CLOSED면 {@code null}(등록 스킵).
     *
     * @param exchange tradable_stocks.exchange (NASDAQ/NYSE/AMEX)
     * @param symbol   tradable_stocks.stock_code (예 AAPL)
     * @throws BusinessException 심볼이 비었거나, 매핑 없는 거래소일 때
     */
    public static String of(MarketSession session, String exchange, String symbol) {
        if (session == MarketSession.CLOSED) {
            return null;
        }
        if (symbol == null || symbol.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종목 심볼 없음");
        }
        OverseasMarket market = OverseasMarket.fromExchange(exchange);

        return switch (session) {
            case REGULAR -> "D" + market.regularCode() + symbol;   // 예: D + NAS + AAPL
            case DAY -> "R" + market.dayCode() + symbol;           // 예: R + BAQ + AAPL
            case CLOSED -> null;                                   // 위에서 처리, 컴파일러 완전성용
        };
    }
}
