package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.client.LsMarketClient;
import com.pocketstock.ledger.trading.client.LsT1102Response;
import com.pocketstock.ledger.trading.dto.StockPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final LsMarketClient lsMarketClient;

    /** 국내 현재가 조회(t1102). sign으로 전일대비/등락율 부호를 적용한다. */
    public StockPriceResponse getDomesticPrice(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LsT1102Response.OutBlock ob = lsMarketClient.getDomesticPrice(stockCode);

        int factor = isDown(ob.sign()) ? -1 : 1;  // 4하한·5하락 → 음수
        long changePrice = factor * Math.abs(ob.change());
        BigDecimal changeRate = parseDiff(ob.diff()).abs().multiply(BigDecimal.valueOf(factor));

        return new StockPriceResponse(
                stockCode,
                ob.price(),
                changePrice,
                changeRate,
                ob.high(),
                ob.low(),
                ob.open(),
                ob.volume());
    }

    private boolean isDown(String sign) {
        return "4".equals(sign) || "5".equals(sign);
    }

    private BigDecimal parseDiff(String diff) {
        try {
            return new BigDecimal(diff.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
