package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 국내 실시간 호가(UH1 통합호가잔량). 진입 시 {@link OrderbookResponse}(t8450) 스냅샷을
 * 받은 뒤, 이 페이로드로 사다리를 갱신한다 — asks/bids 의 Level 모양을 스냅샷과 동일하게 맞춰
 * 프론트가 같은 렌더링 로직을 재사용한다.
 *
 * <p>잔량은 통합(KRX+NXT, unt_*) 기준 — 명세의 "(통합)호가잔량"과 일치.
 * 가격(offerho/bidho)은 거래소 무관 단일값. UH1엔 현재가/상하한가가 없어
 * 그 둘은 스냅샷(OrderbookResponse)이 책임지고, 여기선 호가시간·누적거래량을 더한다.
 */
public record AskingResponse(
        String stockCode,
        String quoteTime,             // hotime 호가시간
        List<Level> asks,             // 매도 1~10 (rank 1 = 최우선=최저)
        List<Level> bids,             // 매수 1~10 (rank 1 = 최우선=최고)
        BigDecimal totalAskVolume,    // unt_totofferrem 통합 총매도잔량
        BigDecimal totalBidVolume,    // unt_totbidrem 통합 총매수잔량
        BigDecimal accumVolume        // volume 누적거래량
) {
    /** 호가 한 단계. rank 1~10, price=호가, volume=통합잔량. {@link OrderbookResponse.Level}와 동형. */
    public record Level(int rank, BigDecimal price, BigDecimal volume) {
    }
}
