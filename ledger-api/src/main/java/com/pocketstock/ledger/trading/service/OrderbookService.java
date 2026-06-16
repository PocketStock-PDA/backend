package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.client.LsMarketClient;
import com.pocketstock.ledger.trading.client.LsT8450Response;
import com.pocketstock.ledger.trading.dto.OrderbookResponse;
import com.pocketstock.ledger.trading.dto.OrderbookResponse.Level;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 국내 호가 조회(t8450). 온주 주문 화면 진입 시 호가창 스냅샷을 합성한다.
 */
@Service
@RequiredArgsConstructor
public class OrderbookService {

    private static final int LEVELS = 10;

    private final LsMarketClient lsMarketClient;

    /** 국내 호가창 조회 — 매도·매수 10단계 + 현재가/상하한가/잔량합. */
    public OrderbookResponse getDomesticOrderbook(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LsT8450Response.OutBlock ob = lsMarketClient.getDomesticOrderbook(stockCode);

        List<Level> asks = toLevels(ob.askPrices(), ob.askVolumes());
        List<Level> bids = toLevels(ob.bidPrices(), ob.bidVolumes());

        return new OrderbookResponse(
                stockCode,
                BigDecimal.valueOf(ob.price()),
                BigDecimal.valueOf(ob.upperLimit()),
                BigDecimal.valueOf(ob.lowerLimit()),
                asks,
                bids,
                BigDecimal.valueOf(ob.offerTotal()),
                BigDecimal.valueOf(ob.bidTotal()));
    }

    /** 호가/잔량 배열(최우선=index 0) → rank 1~10 Level 목록. */
    private List<Level> toLevels(long[] prices, long[] volumes) {
        List<Level> levels = new ArrayList<>(LEVELS);
        for (int i = 0; i < LEVELS; i++) {
            levels.add(new Level(i + 1, BigDecimal.valueOf(prices[i]), BigDecimal.valueOf(volumes[i])));
        }
        return levels;
    }
}
