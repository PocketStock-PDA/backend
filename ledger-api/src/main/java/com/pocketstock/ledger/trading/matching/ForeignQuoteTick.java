package com.pocketstock.ledger.trading.matching;

import java.math.BigDecimal;

/**
 * 해외(KIS HDFSASP0) 실시간 호가 1틱 — 매칭 엔진 내부 전달용 경량 이벤트.
 * {@code ForeignQuoteListener}가 STOMP 팬아웃과 함께 발행, {@link WholeOrderMatchingEngine}이 소비한다.
 * KIS 프레임의 SYMB(안정적 종목코드, 예 AAPL)를 stockCode로 쓴다 — 세션마다 바뀌는 RSYM이 아니라
 * SYMB라서 PENDING 인덱스(종목코드 키)와 그대로 매칭된다(별도 역매핑 테이블 불필요).
 * asks[0]/bids[0]이 최우선(매도=최저·매수=최고) — 국내 {@link DomesticQuoteTick}과 동일 규약.
 */
public record ForeignQuoteTick(
        String stockCode,
        BigDecimal[] askPrices,
        BigDecimal[] askVolumes,
        BigDecimal[] bidPrices,
        BigDecimal[] bidVolumes) implements QuoteTick {
}
