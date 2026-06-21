package com.pocketstock.ledger.trading.matching;

import java.math.BigDecimal;

/**
 * 국내(LS UH1) 실시간 호가 1틱 — 매칭 엔진 내부 전달용 경량 이벤트.
 * {@code AskingRealtimeListener}가 STOMP 팬아웃과 함께 발행, {@link WholeOrderMatchingEngine}이 소비한다.
 * asks[0]/bids[0]이 최우선(매도=최저·매수=최고) — 사다리 순서 그대로 cross 판정·훑기에 쓴다.
 */
public record DomesticQuoteTick(
        String stockCode,
        BigDecimal[] askPrices,
        BigDecimal[] askVolumes,
        BigDecimal[] bidPrices,
        BigDecimal[] bidVolumes) implements QuoteTick {
}
