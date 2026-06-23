package com.pocketstock.ledger.trading.support;

import com.pocketstock.ledger.trading.domain.TradableStock;

/**
 * 해외 종목의 KIS REST 거래소코드(EXCD) 도출 — 세션에 따라 정규장/주간거래 코드로 분기한다.
 * 현재가·호가 등 KIS REST 호출의 시장 구분(EXCD 쿼리)에 쓴다.
 *
 * <p><b>REST EXCD는 prefix가 없다</b>(KIS 기본시세 문서 확인): 정규 {@code NAS}/{@code NYS}/{@code AMS},
 * 주간 {@code BAQ}/{@code BAY}/{@code BAA}. D/R prefix(DNAS·RBAQ)는 실시간(WS) tr_key·응답 rsym 전용이며
 * 그쪽은 {@link KisTrKey}가 담당한다 — 둘 다 {@link OverseasMarket}(거래소→코드 단일 진실원)에서 파생.
 *
 * <p>세션 마감(CLOSED)·미수신이면 정규 코드로 폴백한다 — 라이브가 없으면 read-through가 빈 응답을 받아
 * 마지막 스냅샷(동결가)으로 떨어진다(#145 통합 체결 정책).
 */
public final class OverseasExchangeCode {

    private OverseasExchangeCode() {
    }

    /**
     * (세션 + 종목) → KIS REST EXCD. 주간거래(DAY)면 주간 코드(BAQ 등), 그 외(정규·마감)면 정규 코드(NAS 등).
     * @throws com.pocketstock.common.exception.BusinessException 매핑 없는 거래소일 때(INVALID_INPUT).
     */
    public static String of(MarketSession session, TradableStock stock) {
        OverseasMarket market = OverseasMarket.fromExchange(stock.getExchange());
        return session == MarketSession.DAY ? market.dayCode() : market.regularCode();
    }
}
