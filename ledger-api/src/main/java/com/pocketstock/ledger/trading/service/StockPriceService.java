package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.kis.KisMarketClient;
import com.pocketstock.ledger.kis.KisPriceDetailResponse;
import com.pocketstock.ledger.trading.client.LsMarketClient;
import com.pocketstock.ledger.trading.client.LsT1102Response;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.StockPriceResponse;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.support.MarketSessionResolver;
import com.pocketstock.ledger.trading.support.MarketSnapshotCache;
import com.pocketstock.ledger.trading.support.OverseasExchangeCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;
import static com.pocketstock.ledger.trading.support.MarketFields.lng;

/**
 * 현재가 조회. 국내=LS t1102, 해외=KIS 현재가상세(HHDFS76200200).
 *
 * <p>vendor-first + 캐시 폴백(#128): 벤더 REST가 정상 현재가를 주면 그대로 반환하며 캐시에 갱신하고,
 * 호출이 실패하면 마지막 캐시 스냅샷을 반환한다(응답 {@code asOf}로 staleness 표시). 캐시는 보여주기 전용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private static final int RATE_SCALE = 2;
    private static final String TYPE_PRICE_DOMESTIC = "price-domestic";
    private static final String TYPE_PRICE_FOREIGN = "price-foreign";

    private final LsMarketClient lsMarketClient;
    private final KisMarketClient kisMarketClient;
    private final StockMapper stockMapper;
    private final MarketSnapshotCache snapshotCache;
    private final MarketSessionResolver marketSessionResolver;

    /** 국내 현재가 조회(t1102). sign으로 전일대비/등락율 부호를 적용한다. 실패 시 마지막 캐시. */
    public StockPriceResponse getDomesticPrice(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return snapshotCache.readThrough(TYPE_PRICE_DOMESTIC, stockCode,
                () -> buildDomesticPrice(stockCode), StockPriceService::hasPrice,
                StockPriceResponse.class, "국내 현재가");
    }

    private StockPriceResponse buildDomesticPrice(String stockCode) {
        LsT1102Response.OutBlock ob = lsMarketClient.getDomesticPrice(stockCode);
        int factor = isDown(ob.sign()) ? -1 : 1;  // 4하한·5하락 → 음수
        long changePrice = factor * Math.abs(ob.change());
        BigDecimal changeRate = parseDiff(ob.diff(), stockCode).abs().multiply(BigDecimal.valueOf(factor));
        return new StockPriceResponse(
                stockCode,
                BigDecimal.valueOf(ob.price()),
                BigDecimal.valueOf(changePrice),
                changeRate,
                BigDecimal.valueOf(ob.high()),
                BigDecimal.valueOf(ob.low()),
                BigDecimal.valueOf(ob.open()),
                ob.volume(),
                Instant.now().toString());
    }

    /**
     * 해외 현재가 조회(KIS 현재가상세 HHDFS76200200). 국내와 같은 StockPriceResponse로 정렬.
     * KIS는 대비/등락율을 안 주므로 last-base로 계산한다(부호는 자연 부호). 실패 시 마지막 캐시.
     */
    public StockPriceResponse getOverseasPrice(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TradableStock stock = stockMapper.findByCode(stockCode);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }
        // 세션-aware EXCD: 주간거래엔 BAQ(라이브), 정규/마감엔 NAS. 호가(OrderbookService)·WS(KisTrKey)와 정합.
        String excd = OverseasExchangeCode.of(marketSessionResolver.current(), stock);
        return snapshotCache.readThrough(TYPE_PRICE_FOREIGN, stockCode,
                () -> buildOverseasPrice(stock, excd), StockPriceService::hasPrice,
                StockPriceResponse.class, "해외 현재가");
    }

    private StockPriceResponse buildOverseasPrice(TradableStock stock, String excd) {
        KisPriceDetailResponse.Output out = kisMarketClient.getOverseasPriceDetail(excd, stock.getStockCode());
        BigDecimal last = dec(out.last());
        BigDecimal base = dec(out.base());
        BigDecimal change = last.subtract(base);
        BigDecimal changeRate = base.signum() == 0
                ? BigDecimal.ZERO
                : change.divide(base, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(RATE_SCALE, RoundingMode.HALF_UP);
        return new StockPriceResponse(
                stock.getStockCode(), last, change, changeRate,
                dec(out.high()), dec(out.low()), dec(out.open()), lng(out.tvol()), Instant.now().toString());
    }

    private static boolean hasPrice(StockPriceResponse r) {
        return r.currentPrice() != null && r.currentPrice().signum() > 0;
    }

    private boolean isDown(String sign) {
        return "4".equals(sign) || "5".equals(sign);
    }

    private BigDecimal parseDiff(String diff, String stockCode) {
        try {
            return new BigDecimal(diff.trim());
        } catch (Exception e) {
            // 등락율 파싱 실패는 데이터 이상 신호 → 0으로 대체하되 묵살하지 않고 경고
            log.warn("등락율(diff) 파싱 실패 stockCode={}, diff='{}' → 0 처리", stockCode, diff);
            return BigDecimal.ZERO;
        }
    }
}
