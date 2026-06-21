package com.pocketstock.ledger.trading.matching;

import java.math.BigDecimal;

/**
 * 매칭 엔진이 소비하는 호가 1틱의 공통 모양 — 국내(LS UH1)·해외(KIS HDFSASP0)를 한 타입으로 받는다.
 * 사다리는 rank 1 = 최우선(매도=최저·매수=최고) 순서. cross 판정·{@code BookWalker.walk}가 그대로 쓴다.
 * 구현체: {@link DomesticQuoteTick}(국내)·{@link ForeignQuoteTick}(해외). 종목의 국내/해외 구분은
 * 틱이 아니라 PENDING 스냅샷(exchange)이 들고 있으므로 여기엔 시장 정보가 없다.
 */
public interface QuoteTick {

    String stockCode();

    BigDecimal[] askPrices();

    BigDecimal[] askVolumes();

    BigDecimal[] bidPrices();

    BigDecimal[] bidVolumes();
}
