package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 호가창 스냅샷(국내 t8450, 통합 KRX+NXT). 진입 시 1회 조회용 — 이후 실시간 갱신은 WS(UH1 통합).
 * asks(매도)·bids(매수) 모두 rank 1 = 최우선 호가.
 */
public record OrderbookResponse(
        String stockCode,
        BigDecimal currentPrice,
        BigDecimal upperLimit,
        BigDecimal lowerLimit,
        List<Level> asks,
        List<Level> bids,
        BigDecimal totalAskVolume,
        BigDecimal totalBidVolume
) {
    /** 호가 한 단계. rank 1~10, price=호가, volume=잔량. */
    public record Level(int rank, BigDecimal price, BigDecimal volume) {
    }
}
