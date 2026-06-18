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
import com.pocketstock.ledger.trading.support.OverseasExchangeCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;
import static com.pocketstock.ledger.trading.support.MarketFields.lng;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private static final int RATE_SCALE = 2;

    private final LsMarketClient lsMarketClient;
    private final KisMarketClient kisMarketClient;
    private final StockMapper stockMapper;

    /** 국내 현재가 조회(t1102). sign으로 전일대비/등락율 부호를 적용한다. */
    public StockPriceResponse getDomesticPrice(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

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
                ob.volume());
    }

    /**
     * 해외 현재가 조회(KIS 현재가상세 HHDFS76200200). 국내와 같은 StockPriceResponse로 정렬.
     * KIS는 대비/등락율을 안 주므로 last-base로 계산한다(부호는 자연 부호).
     */
    public StockPriceResponse getOverseasPrice(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TradableStock stock = stockMapper.findByCode(stockCode);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }

        String excd = OverseasExchangeCode.of(stock);
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
                stockCode, last, change, changeRate,
                dec(out.high()), dec(out.low()), dec(out.open()), lng(out.tvol()));
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
