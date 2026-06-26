package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.dto.PortfolioSummaryResponse;
import com.pocketstock.ledger.trading.dto.PortfolioSummaryResponse.HoldingValuation;
import com.pocketstock.ledger.trading.dto.PortfolioSummaryResponse.OverseasSegment;
import com.pocketstock.ledger.trading.dto.PortfolioSummaryResponse.Segment;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 포트폴리오 요약 — 보유(holdings) × 현재가 스냅샷을 전체/국내/해외로 집계하고 종목별 평가·수익률을 함께 낸다.
 * 화면 상단 총합과 보유 카드의 단일 소스(프론트 클라이언트 합성 교체용).
 *
 * <p>원금(invested) = 취득원가. 국내는 실원화(krw_cost_basis), 해외는 수량×평균매입가(USD).
 * 환산 KRW는 현재 환율(USD/KRW) 1회 캐시로 계산하고, 해외 USD 수익률은 환차 제외(전체 KRW 수익률은 환차 포함).
 * 현재가/환율 조회 실패 종목은 집계에서 제외(priced=false)해 0원 오인을 막는다 — {@link PortfolioValuationService}와 동일 정책.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {

    private static final String KRW = "KRW";
    private static final int KRW_SCALE = 0;     // 원화 정수
    private static final int USD_SCALE = 2;     // 달러 센트
    private static final int RATE_SCALE = 2;    // 수익률 %

    private final HoldingMapper holdingMapper;
    private final StockPriceService stockPriceService;
    private final CurrencyRateProvider currencyRateProvider;

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<Holding> holdings = holdingMapper.findByUserId(userId);
        boolean hasOverseas = holdings.stream().anyMatch(h -> !KRW.equals(h.getCurrency()));
        BigDecimal usdKrwRate = hasOverseas ? fetchUsdKrwRateOrNull() : null;

        List<HoldingValuation> items = new ArrayList<>(holdings.size());

        // 국내(KRW) 집계
        BigDecimal domEval = BigDecimal.ZERO;
        BigDecimal domInvested = BigDecimal.ZERO;
        // 해외(USD native) 집계 + 환산 KRW 집계
        BigDecimal ovsEvalUsd = BigDecimal.ZERO;
        BigDecimal ovsInvestedUsd = BigDecimal.ZERO;
        BigDecimal ovsEvalKrw = BigDecimal.ZERO;
        BigDecimal ovsInvestedKrw = BigDecimal.ZERO;

        for (Holding h : holdings) {
            boolean domestic = KRW.equals(h.getCurrency());
            BigDecimal qty = h.getQuantity() == null ? BigDecimal.ZERO : h.getQuantity();
            BigDecimal avg = h.getAvgBuyPrice() == null ? BigDecimal.ZERO : h.getAvgBuyPrice();
            BigDecimal krwCost = h.getKrwCostBasis() == null ? BigDecimal.ZERO : h.getKrwCostBasis();

            // native 원금: 국내=실원화, 해외=수량×평균매입가(USD)
            BigDecimal invested = domestic ? krwCost : qty.multiply(avg);

            BigDecimal price = currentPriceOrNull(userId, h.getStockCode(), domestic);
            boolean priced = price != null;
            BigDecimal evalAmount = priced ? qty.multiply(price) : null;
            BigDecimal profit = priced ? evalAmount.subtract(invested) : null;
            BigDecimal profitRate = priced ? rate(profit, invested) : null;

            BigDecimal evalKrw;
            if (!priced) {
                evalKrw = null;
            } else if (domestic) {
                evalKrw = scaleKrw(evalAmount);
            } else {
                evalKrw = usdKrwRate != null ? scaleKrw(evalAmount.multiply(usdKrwRate)) : null;
            }
            // 환산 KRW 원금 = 매수시점 환율 취득원가(krw_cost_basis). 국내는 invested와 동일.
            BigDecimal investedKrw = scaleKrw(krwCost);
            BigDecimal profitKrw = evalKrw != null ? evalKrw.subtract(investedKrw) : null;

            // 집계는 현재가(+해외는 환율) 확보된 종목만
            if (priced) {
                if (domestic) {
                    domEval = domEval.add(evalAmount);
                    domInvested = domInvested.add(invested);
                } else if (usdKrwRate != null) {
                    ovsEvalUsd = ovsEvalUsd.add(evalAmount);
                    ovsInvestedUsd = ovsInvestedUsd.add(invested);
                    ovsEvalKrw = ovsEvalKrw.add(evalAmount.multiply(usdKrwRate));
                    ovsInvestedKrw = ovsInvestedKrw.add(krwCost);
                }
            }

            BigDecimal frac = h.getFractionalQty() == null ? BigDecimal.ZERO : h.getFractionalQty();
            int scale = domestic ? KRW_SCALE : USD_SCALE;
            items.add(new HoldingValuation(
                    h.getStockCode(),
                    h.getCurrency(),
                    qty,
                    qty.subtract(frac),            // 온주 = 총 − 소수
                    frac,
                    avg,
                    priced ? scaleNative(price, scale) : null,
                    priced ? scaleNative(evalAmount, scale) : null,
                    scaleNative(invested, scale),
                    priced ? scaleNative(profit, scale) : null,
                    profitRate,
                    evalKrw,
                    investedKrw,
                    profitKrw,
                    priced));
        }

        Segment domestic = segment(domEval, domInvested);
        OverseasSegment overseas = hasOverseas
                ? overseasSegment(ovsEvalUsd, ovsInvestedUsd, ovsEvalKrw, ovsInvestedKrw)
                : null;
        // 전체(환산 KRW) = 국내 + 해외 환산
        Segment total = segment(domEval.add(ovsEvalKrw), domInvested.add(ovsInvestedKrw));

        return new PortfolioSummaryResponse(
                LocalDateTime.now().toString(),
                usdKrwRate,
                total,
                domestic,
                overseas,
                items);
    }

    private Segment segment(BigDecimal evalKrw, BigDecimal investedKrw) {
        BigDecimal profit = evalKrw.subtract(investedKrw);
        return new Segment(
                scaleKrw(evalKrw),
                scaleKrw(investedKrw),
                scaleKrw(profit),
                rate(profit, investedKrw));
    }

    private OverseasSegment overseasSegment(BigDecimal evalUsd, BigDecimal investedUsd,
                                            BigDecimal evalKrw, BigDecimal investedKrw) {
        BigDecimal profitUsd = evalUsd.subtract(investedUsd);
        BigDecimal profitKrw = evalKrw.subtract(investedKrw);
        return new OverseasSegment(
                scaleNative(evalUsd, USD_SCALE),
                scaleNative(investedUsd, USD_SCALE),
                scaleNative(profitUsd, USD_SCALE),
                rate(profitUsd, investedUsd),    // USD 기준(환차 제외)
                scaleKrw(evalKrw),
                scaleKrw(investedKrw),
                scaleKrw(profitKrw));            // 환차 포함
    }

    /** 수익률 % = profit / invested × 100. 원금 0이면 0(웰컴보상 등 0원가 보유분 보호). */
    private BigDecimal rate(BigDecimal profit, BigDecimal invested) {
        if (invested == null || invested.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return profit.divide(invested, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleKrw(BigDecimal v) {
        return v.setScale(KRW_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleNative(BigDecimal v, int scale) {
        return v == null ? null : v.setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal fetchUsdKrwRateOrNull() {
        try {
            return currencyRateProvider.current().exchangeRate();
        } catch (Exception e) {
            log.warn("포트폴리오 요약 환율(USD/KRW) 조회 실패 — 해외 환산 제외: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal currentPriceOrNull(Long userId, String stockCode, boolean domestic) {
        try {
            BigDecimal price = domestic
                    ? stockPriceService.getDomesticPrice(userId, stockCode).currentPrice()
                    : stockPriceService.getOverseasPrice(userId, stockCode).currentPrice();
            return (price != null && price.signum() > 0) ? price : null;
        } catch (Exception e) {
            log.warn("포트폴리오 요약 현재가 조회 실패 (userId={}, stock={}): {}", userId, stockCode, e.getMessage());
            return null;
        }
    }
}
