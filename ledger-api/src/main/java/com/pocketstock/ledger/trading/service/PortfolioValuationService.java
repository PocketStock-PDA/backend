package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 퍼즐판 실시간 평가 — 보유 종목(holdings) × 현재가 합(KRW). 포트폴리오 화면과 동일 소스(현재가).
 * 해외(USD) 종목은 보유통화 평가액에 USD/KRW 환율을 곱해 KRW로 환산한다.
 * 현재가 조회는 StockPriceService(스냅샷 캐시·폴백)에 위임하고, 개별 종목 실패는 건너뛰어(부분 합) 화면을 막지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioValuationService {

    private static final String KRW = "KRW";

    private final HoldingMapper holdingMapper;
    private final StockPriceService stockPriceService;
    private final CurrencyRateProvider currencyRateProvider;

    @Transactional(readOnly = true)
    public BigDecimal getPuzzleValuationKrw(Long userId) {
        List<Holding> holdings = holdingMapper.findByUserId(userId);
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal usdKrwRate = null;   // 해외 보유가 있을 때만 1회 조회
        BigDecimal total = BigDecimal.ZERO;

        for (Holding h : holdings) {
            BigDecimal qty = h.getQuantity();
            if (qty == null || qty.signum() <= 0) {
                continue;
            }

            boolean domestic = KRW.equals(h.getCurrency());
            BigDecimal price = currentPriceOrNull(userId, h.getStockCode(), domestic);
            if (price == null) {
                continue;   // 현재가 조회 실패 종목은 합산에서 제외(부분 평가)
            }

            BigDecimal valuation = qty.multiply(price);
            if (!domestic) {
                if (usdKrwRate == null) {
                    usdKrwRate = currencyRateProvider.current().exchangeRate();
                }
                valuation = valuation.multiply(usdKrwRate);
            }
            total = total.add(valuation);
        }

        return total.setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal currentPriceOrNull(Long userId, String stockCode, boolean domestic) {
        try {
            BigDecimal price = domestic
                    ? stockPriceService.getDomesticPrice(userId, stockCode).currentPrice()
                    : stockPriceService.getOverseasPrice(userId, stockCode).currentPrice();
            return (price != null && price.signum() > 0) ? price : null;
        } catch (Exception e) {
            log.warn("퍼즐 평가 현재가 조회 실패 (userId={}, stock={}): {}", userId, stockCode, e.getMessage());
            return null;
        }
    }
}
