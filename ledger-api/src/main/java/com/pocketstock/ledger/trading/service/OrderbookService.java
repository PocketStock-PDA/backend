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
import com.pocketstock.ledger.trading.support.MarketSnapshotCache;
import com.pocketstock.ledger.trading.support.OverseasExchangeCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;

/**
 * 호가 조회. 국내=LS t8450(통합), 해외=KIS 현재가호가(HHDFS76200100).
 * 온주 주문 화면 진입 시 호가창 스냅샷을 합성한다(이후 실시간 갱신은 WS).
 *
 * <p>vendor-first + 캐시 폴백(#128): 벤더 REST가 정상 호가를 주면 그대로 반환하며 마지막 스냅샷으로
 * 캐시에 갱신하고, 장 마감/공백으로 호가창이 비었거나 호출이 실패하면 마지막 캐시 스냅샷을 반환한다
 * (응답 {@code asOf}가 과거값이면 프론트가 "장마감 기준" 등으로 표시). 캐시는 조회·체결 공용(#145 통합 체결 정책).
 */
@Service
@RequiredArgsConstructor
public class OrderbookService {

    private static final int LEVELS = 10;
    private static final String TYPE_DOMESTIC = "orderbook";
    private static final String TYPE_FOREIGN = "orderbook-foreign";

    private final LsMarketClient lsMarketClient;
    private final KisMarketClient kisMarketClient;
    private final StockMapper stockMapper;
    private final MarketSnapshotCache snapshotCache;

    /** 국내 호가창 조회 — 매도·매수 10단계 + 현재가/상하한가/잔량합. 빈 호가창·실패 시 마지막 캐시. */
    public OrderbookResponse getDomesticOrderbook(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return domesticSnapshot(stockCode);
    }

    /** 체결용(인증 없이) — 스냅샷 백업 국내 호가창. 빈 호가창·실패 시 마지막 스냅샷(동결가). #145 */
    OrderbookResponse domesticSnapshot(String stockCode) {
        return snapshotCache.readThrough(TYPE_DOMESTIC, stockCode,
                () -> buildDomestic(stockCode), OrderbookService::hasBook,
                OrderbookResponse.class, "국내 호가");
    }

    private OrderbookResponse buildDomestic(String stockCode) {
        LsT8450Response.OutBlock ob = lsMarketClient.getDomesticOrderbook(stockCode);
        return new OrderbookResponse(
                stockCode,
                BigDecimal.valueOf(ob.price()),
                BigDecimal.valueOf(ob.upperLimit()),
                BigDecimal.valueOf(ob.lowerLimit()),
                toLevels(ob.askPrices(), ob.askVolumes()),
                toLevels(ob.bidPrices(), ob.bidVolumes()),
                BigDecimal.valueOf(ob.offerTotal()),
                BigDecimal.valueOf(ob.bidTotal()),
                Instant.now().toString());
    }

    /**
     * 해외 호가창 조회(KIS HHDFS76200100). WS 호가(ForeignQuoteResponse)와 동일 DTO·동일 rank 계약:
     * asks rank n = pask{n}(오름차순), bids rank n = pbid{n}(내림차순). 빈 호가창·실패 시 마지막 캐시.
     */
    public ForeignQuoteResponse getOverseasOrderbook(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TradableStock stock = stockMapper.findByCode(stockCode);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }
        return overseasSnapshot(stock);
    }

    /** 체결용(인증 없이) — 스냅샷 백업 해외 호가창. 빈 호가창·실패 시 마지막 스냅샷(동결가). #145 */
    ForeignQuoteResponse overseasSnapshot(TradableStock stock) {
        String excd = OverseasExchangeCode.of(stock);
        return snapshotCache.readThrough(TYPE_FOREIGN, stock.getStockCode(),
                () -> buildOverseas(stock, excd), OrderbookService::hasForeignBook,
                ForeignQuoteResponse.class, "해외 호가");
    }

    private ForeignQuoteResponse buildOverseas(TradableStock stock, String excd) {
        KisAskingPriceResponse res = kisMarketClient.getOverseasOrderbook(excd, stock.getStockCode());
        KisAskingPriceResponse.Output1 o1 = res.output1();
        KisAskingPriceResponse.Output2 o2 = res.output2();
        return new ForeignQuoteResponse(
                o1.code(),
                o1.rsym(),
                o1.dhms(),
                toForeignLevels(o2.askPrices(), o2.askVolumes()),
                toForeignLevels(o2.bidPrices(), o2.bidVolumes()),
                dec(o1.avol()),
                dec(o1.bvol()),
                Instant.now().toString());
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

    /** 호가창이 유효한가(빈 호가창=마감/공백 판별) — 최우선 매도/매수 중 하나라도 양수면 유효. */
    private static boolean hasBook(OrderbookResponse r) {
        return firstPositive(firstPrice(r.asks())) || firstPositive(firstPrice(r.bids()));
    }

    private static boolean hasForeignBook(ForeignQuoteResponse r) {
        return firstPositive(firstForeignPrice(r.asks())) || firstPositive(firstForeignPrice(r.bids()));
    }

    private static BigDecimal firstPrice(List<Level> levels) {
        return (levels == null || levels.isEmpty()) ? null : levels.get(0).price();
    }

    private static BigDecimal firstForeignPrice(List<ForeignQuoteResponse.Level> levels) {
        return (levels == null || levels.isEmpty()) ? null : levels.get(0).price();
    }

    private static boolean firstPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
