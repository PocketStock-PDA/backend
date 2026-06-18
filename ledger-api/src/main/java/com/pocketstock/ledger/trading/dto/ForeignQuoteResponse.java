package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 해외주식 실시간 호가(KIS HDFSASP0). 미국 매수/매도 각 10호가.
 * 국내 호가({@link AskingResponse})와 같은 Level 모양으로 맞춰 프론트가 렌더링을 재사용한다.
 *
 * <p>{@code realtimeCode}는 KIS 실시간 종목코드(RSYM, 예: RBAQAAPL)로, 구독 tr_key와 동일.
 * 토픽 destination의 키로 쓰인다. {@code symbol}은 순수 티커(SYMB, 예: AAPL).
 */
public record ForeignQuoteResponse(
        String symbol,                // SYMB (예: AAPL)
        String realtimeCode,          // RSYM (예: RBAQAAPL) — 토픽 키
        String localTime,             // XHMS 현지시간
        List<Level> asks,             // 매도 1~10 (rank 1 = 최우선=최저)
        List<Level> bids,             // 매수 1~10 (rank 1 = 최우선=최고)
        BigDecimal totalAskVolume,    // AVOL 매도총잔량
        BigDecimal totalBidVolume     // BVOL 매수총잔량
) {
    /** 호가 한 단계. rank 1~10, price=호가, volume=잔량. {@link AskingResponse.Level}와 동형. */
    public record Level(int rank, BigDecimal price, BigDecimal volume) {
    }
}
