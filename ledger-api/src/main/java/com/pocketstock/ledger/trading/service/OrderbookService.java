package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.kis.KisAskingPriceResponse;
import com.pocketstock.ledger.kis.KisMarketClient;
import com.pocketstock.ledger.trading.client.LsMarketClient;
import com.pocketstock.ledger.trading.client.LsT8450Response;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse;
import com.pocketstock.ledger.trading.dto.OrderbookResponse;
import com.pocketstock.ledger.trading.dto.OrderbookResponse.Level;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.support.OverseasExchangeCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;

/**
 * 호가 조회. 국내=LS t8450(통합), 해외=KIS 현재가호가(HHDFS76200100).
 * 온주 주문 화면 진입 시 호가창 스냅샷을 합성한다(이후 실시간 갱신은 WS).
 */
@Service
@RequiredArgsConstructor
public class OrderbookService {

    private static final int LEVELS = 10;

    private final LsMarketClient lsMarketClient;
    private final KisMarketClient kisMarketClient;
    private final StockMapper stockMapper;

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

    /**
     * 해외 호가창 조회(KIS HHDFS76200100). WS 호가(ForeignQuoteResponse)와 동일 DTO·동일 rank 계약:
     * asks rank n = pask{n}(오름차순), bids rank n = pbid{n}(내림차순).
     */
    public ForeignQuoteResponse getOverseasOrderbook(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TradableStock stock = stockMapper.findByCode(stockCode);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }

        String excd = OverseasExchangeCode.of(stock);
        KisAskingPriceResponse res = kisMarketClient.getOverseasOrderbook(excd, stock.getStockCode());
        KisAskingPriceResponse.Output1 o1 = res.output1();
        KisAskingPriceResponse.Output2 o2 = res.output2();

        List<ForeignQuoteResponse.Level> asks = toForeignLevels(o2.askPrices(), o2.askVolumes());
        List<ForeignQuoteResponse.Level> bids = toForeignLevels(o2.bidPrices(), o2.bidVolumes());

        return new ForeignQuoteResponse(
                o1.code(),
                o1.rsym(),
                o1.dhms(),
                asks,
                bids,
                dec(o1.avol()),
                dec(o1.bvol()));
    }

    /** 호가/잔량 배열(최우선=index 0) → rank 1~10 Level 목록. */
    private List<Level> toLevels(long[] prices, long[] volumes) {
        List<Level> levels = new ArrayList<>(LEVELS);
        for (int i = 0; i < LEVELS; i++) {
            levels.add(new Level(i + 1, BigDecimal.valueOf(prices[i]), BigDecimal.valueOf(volumes[i])));
        }
        return levels;
    }

    private List<ForeignQuoteResponse.Level> toForeignLevels(String[] prices, String[] volumes) {
        List<ForeignQuoteResponse.Level> levels = new ArrayList<>(LEVELS);
        for (int i = 0; i < LEVELS; i++) {
            levels.add(new ForeignQuoteResponse.Level(i + 1, dec(prices[i]), dec(volumes[i])));
        }
        return levels;
    }

}
