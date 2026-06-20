package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateCache;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import com.pocketstock.ledger.kis.KisRankingClient;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.domain.WelcomeReward;
import com.pocketstock.ledger.trading.dto.WelcomeRewardCandidateResponse;
import com.pocketstock.ledger.trading.dto.WelcomeRewardResponse;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.mapper.WelcomeRewardMapper;
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
 * 웰컴 보상 — 온보딩(계좌개설+연동) 완료 후 1회성 첫 주식 선물.
 * 후보: 국내 거래대금 1·2위 + 해외(NASDAQ) 1·2위 = 최대 4종목.
 * 지급: 고른 1종목에 1,000원어치 소수점 주식을 무상으로 holdings에 적립(예수금 차감 없음).
 * 해외는 매매기준율(mid)로 1,000원 → USD 환산 후 수량 산정. 1인 1회(welcome_rewards.user_id UNIQUE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeRewardService {

    /** 해외 후보 거래소 — 현재 나스닥 고정. */
    private static final String OVERSEAS_EXCD = "NAS";
    private static final String MARKET_DOMESTIC = "DOMESTIC";
    private static final String MARKET_OVERSEAS = "OVERSEAS";
    /** 시장별 후보 수(국내 2 + 해외 2 = 4). */
    private static final int PER_MARKET = 2;
    /** 웰컴 보상 예산(원). */
    private static final int BUDGET_KRW = 1_000;
    private static final int QTY_SCALE = 6;     // holdings.quantity DECIMAL(18,6)
    private static final int PRICE_SCALE = 4;   // holdings.avg_buy_price DECIMAL(18,4)
    private static final int FX_SCALE = 8;      // KRW→USD 중간 환산 정밀도

    private final KisRankingClient rankingClient;
    private final StockMapper stockMapper;
    private final SecuritiesAccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final WelcomeRewardMapper rewardMapper;
    private final StockPriceService stockPriceService;
    private final CurrencyRateCache currencyRateCache;

    // ===== 후보 조회 =====

    @Transactional(readOnly = true)
    public List<WelcomeRewardCandidateResponse> getCandidates(Long userId) {
        requireAuth(userId);
        List<WelcomeRewardCandidateResponse> result = new ArrayList<>();
        result.addAll(pick(
                rankingClient.getDomesticTradeAmountRank().stream()
                        .map(i -> new RankedCode(i.stockCode(), i.tradeAmount(), i.rank()))
                        .toList(),
                MARKET_DOMESTIC));
        result.addAll(pick(
                rankingClient.getOverseasTradeAmountRank(OVERSEAS_EXCD).stream()
                        .map(i -> new RankedCode(i.symb(), i.tradeAmount(), i.rank()))
                        .toList(),
                MARKET_OVERSEAS));
        return result;
    }

    /** 순위 상위부터 거래가능·소수점가능 종목만 PER_MARKET개 보강. */
    private List<WelcomeRewardCandidateResponse> pick(List<RankedCode> ranked, String market) {
        List<WelcomeRewardCandidateResponse> out = new ArrayList<>();
        for (RankedCode row : ranked) {
            if (out.size() >= PER_MARKET) {
                break;
            }
            TradableStock stock = stockMapper.findByCode(row.code());
            if (!isGrantable(stock)) {
                continue;   // 종목마스터 미존재/거래정지/소수점불가 → 후보 제외
            }
            out.add(new WelcomeRewardCandidateResponse(
                    stock.getStockCode(),
                    stock.getStockName(),
                    market,
                    stock.getExchange(),
                    stock.getCurrency(),
                    parseAmount(row.tradeAmount()),
                    parseRank(row.rank()),
                    stock.getLogoUrl()));
        }
        if (out.size() < PER_MARKET) {
            log.warn("웰컴 보상 후보 부족: market={} 확보={}/{}", market, out.size(), PER_MARKET);
        }
        return out;
    }

    // ===== 지급 =====

    /**
     * 웰컴 보상 지급 — 고른 종목에 1,000원어치 소수점 주식을 holdings에 적립.
     * holdings 적립 + 지급이력 INSERT를 같은 로컬 트랜잭션으로 처리(DB B). 1인 1회.
     */
    @Transactional
    public WelcomeRewardResponse claim(Long userId, String stockCode) {
        requireAuth(userId);
        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "stockCode는 필수입니다.");
        }
        if (rewardMapper.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 웰컴 보상을 받았습니다.");
        }

        TradableStock stock = stockMapper.findByCode(stockCode);
        if (!isGrantable(stock)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지급할 수 없는 종목입니다: " + stockCode);
        }

        String currency = stock.getCurrency();
        boolean domestic = "KRW".equals(currency);
        String market = domestic ? MARKET_DOMESTIC : MARKET_OVERSEAS;

        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, market);
        if (account == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    market + " 계좌가 없습니다. 먼저 계좌를 개설하세요.");
        }

        BigDecimal price = currentPrice(userId, stockCode, domestic);
        BigDecimal budgetInCcy = domestic
                ? BigDecimal.valueOf(BUDGET_KRW)
                : convertKrwToUsd(BUDGET_KRW);
        BigDecimal quantity = budgetInCcy.divide(price, QTY_SCALE, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지급 수량이 0입니다.");
        }
        BigDecimal grantPrice = price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        upsertHolding(userId, account.getId(), stockCode, currency, quantity, grantPrice);

        LocalDateTime now = LocalDateTime.now();
        WelcomeReward reward = WelcomeReward.builder()
                .userId(userId)
                .accountId(account.getId())
                .stockCode(stockCode)
                .market(market)
                .quantity(quantity)
                .grantPrice(grantPrice)
                .budgetKrw(BUDGET_KRW)
                .currency(currency)
                .grantedAt(now)
                .build();
        rewardMapper.insert(reward);

        return new WelcomeRewardResponse(stockCode, stock.getStockName(), market, currency,
                quantity, grantPrice, BUDGET_KRW, now);
    }

    // ===== 내역 =====

    @Transactional(readOnly = true)
    public List<WelcomeRewardResponse> getHistory(Long userId) {
        requireAuth(userId);
        List<WelcomeRewardResponse> out = new ArrayList<>();
        for (WelcomeReward r : rewardMapper.findByUserId(userId)) {
            TradableStock stock = stockMapper.findByCode(r.getStockCode());
            String name = (stock != null) ? stock.getStockName() : r.getStockCode();
            out.add(new WelcomeRewardResponse(r.getStockCode(), name, r.getMarket(), r.getCurrency(),
                    r.getQuantity(), r.getGrantPrice(), r.getBudgetKrw(), r.getGrantedAt()));
        }
        return out;
    }

    // ===== helpers =====

    private void requireAuth(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private boolean isGrantable(TradableStock stock) {
        return stock != null
                && Boolean.TRUE.equals(stock.getIsActive())
                && Boolean.TRUE.equals(stock.getIsFractional());
    }

    private BigDecimal currentPrice(Long userId, String stockCode, boolean domestic) {
        BigDecimal price = domestic
                ? stockPriceService.getDomesticPrice(userId, stockCode).currentPrice()
                : stockPriceService.getOverseasPrice(userId, stockCode).currentPrice();
        if (price == null || price.signum() <= 0) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "현재가 조회 실패: " + stockCode);
        }
        return price;
    }

    /** 1,000원 등 KRW 금액을 매매기준율(mid)로 USD 환산. 스프레드 미적용(무상 선물). */
    private BigDecimal convertKrwToUsd(int krw) {
        CurrencyRateResponse rate = currencyRateCache.get();
        if (rate == null || rate.exchangeRate() == null || rate.exchangeRate().signum() <= 0) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환율 조회 실패(USD/KRW)");
        }
        return BigDecimal.valueOf(krw).divide(rate.exchangeRate(), FX_SCALE, RoundingMode.HALF_UP);
    }

    /** 보유 적립 — 기존 있으면 수량 합산·가중평균, 없으면 신규. */
    /** 보상 적립 — 매수와 동일한 보유 원자 upsert(수량 누적 + 평단 가중평균). 동시성 안전. */
    private void upsertHolding(Long userId, Long accountId, String stockCode, String currency,
                              BigDecimal quantity, BigDecimal grantPrice) {
        holdingMapper.upsertBuy(userId, accountId, stockCode, quantity, grantPrice, currency);
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static int parseRank(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 순위 응답에서 추린 최소 단위(종목코드·거래대금·순위) — 국내/해외 공통 정규화. */
    private record RankedCode(String code, String tradeAmount, String rank) {
    }
}
