package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.kis.KisRankingClient;
import com.pocketstock.ledger.ls.LsRankingClient;
import com.pocketstock.ledger.ls.LsT1463Response;
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
 * 지급: 고른 1종목에 소수점 최소주문단위(국내 1,000원 / 해외 $1)어치를 무상으로 holdings에 적립(예수금 차감 없음).
 * 수량 산정은 종목 통화 그대로라 환율 불필요. 해외 원화 취득원가만 매매기준율(mid)로 1회 환산해 기록. 1인 1회(welcome_rewards.user_id UNIQUE).
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
    /** 국내 순위 윈도우 — 상위 grantable 2종목을 찾기에 충분한 상위 N. */
    private static final int DOMESTIC_RANK_WINDOW = 30;
    /** 웰컴 후보·지급은 개별주만(순위 API와 동일 정책 — ETF 제외). */
    private static final String SEC_TYPE_STOCK = "STOCK";
    /** LS t1463 value(백만원) → 원 환산(해외 KIS는 원 단위라 표시 스케일 맞춤). */
    private static final BigDecimal LS_VALUE_TO_KRW = BigDecimal.valueOf(1_000_000L);
    /** 웰컴 보상 지급 단위 — 소수점 최소주문단위와 동일(국내 1,000원 / 해외 $1). */
    private static final BigDecimal GRANT_KRW = BigDecimal.valueOf(1_000);
    private static final BigDecimal GRANT_USD = BigDecimal.ONE;
    private static final int QTY_SCALE = 6;     // holdings.quantity DECIMAL(18,6)
    private static final int PRICE_SCALE = 4;   // holdings.avg_buy_price DECIMAL(18,4)

    private final KisRankingClient rankingClient;          // 해외 거래대금 순위(KIS)
    private final LsRankingClient lsRankingClient;          // 국내 거래대금 순위(LS t1463)
    private final StockMapper stockMapper;
    private final SecuritiesAccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final WelcomeRewardMapper rewardMapper;
    private final StockPriceService stockPriceService;
    private final CurrencyRateProvider currencyRateProvider;

    // ===== 후보 조회 =====

    @Transactional(readOnly = true)
    public List<WelcomeRewardCandidateResponse> getCandidates(Long userId) {
        requireAuth(userId);
        List<WelcomeRewardCandidateResponse> result = new ArrayList<>();
        // 국내: LS t1463(거래대금 desc·ETF 제외). 순위는 위치 기반(1부터).
        result.addAll(pick(domesticRanked(), MARKET_DOMESTIC));
        // 해외: KIS 거래대금 순위(NASDAQ).
        result.addAll(pick(
                rankingClient.getOverseasTradeAmountRank(OVERSEAS_EXCD).stream()
                        .map(i -> new RankedCode(i.symb(), i.tradeAmount(), i.rank()))
                        .toList(),
                MARKET_OVERSEAS));
        return result;
    }

    /** 국내 거래대금 순위(LS t1463)를 위치 기반 순위로 정규화. value(백만원)→원 환산해 해외와 스케일 일치. */
    private List<RankedCode> domesticRanked() {
        List<LsT1463Response.Item> rows = lsRankingClient.getDomesticTradeValueRanking(DOMESTIC_RANK_WINDOW);
        List<RankedCode> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            LsT1463Response.Item it = rows.get(i);
            BigDecimal value = (it.value() == null) ? BigDecimal.ZERO : it.value().multiply(LS_VALUE_TO_KRW);
            out.add(new RankedCode(it.shcode(), value.toPlainString(), String.valueOf(i + 1)));
        }
        return out;
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
     * 웰컴 보상 지급 — 고른 종목에 최소주문단위(국내 1,000원 / 해외 $1)어치 소수점 주식을 holdings에 적립.
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
        // 지급 단위는 종목 통화 그대로 — 수량 산정에 환율 불필요(국내 1,000원 / 해외 $1).
        BigDecimal grantUnit = domestic ? GRANT_KRW : GRANT_USD;
        // 지급 단위(예산) 초과 방지로 내림 — quantity×price가 1,000원/$1을 넘지 않게(소수점 주문 경로와 동일 관례).
        BigDecimal quantity = grantUnit.divide(price, QTY_SCALE, RoundingMode.DOWN);
        if (quantity.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지급 수량이 0입니다.");
        }
        BigDecimal grantPrice = price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        // 무상 지급 주식의 원화 취득원가 = 회사 실비용. 국내는 1,000원, 해외는 $1×매매기준율(mid) 1회 환산.
        int budgetKrw = domestic ? GRANT_KRW.intValueExact() : usdToKrw(grantUnit);
        upsertHolding(userId, account.getId(), stockCode, currency, quantity, grantPrice,
                BigDecimal.valueOf(budgetKrw));

        LocalDateTime now = LocalDateTime.now();
        WelcomeReward reward = WelcomeReward.builder()
                .userId(userId)
                .accountId(account.getId())
                .stockCode(stockCode)
                .market(market)
                .quantity(quantity)
                .grantPrice(grantPrice)
                .budgetKrw(budgetKrw)
                .currency(currency)
                .grantedAt(now)
                .build();
        rewardMapper.insert(reward);

        return new WelcomeRewardResponse(stockCode, stock.getStockName(), market, currency,
                quantity, grantPrice, budgetKrw, now);
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
                && Boolean.TRUE.equals(stock.getIsFractional())
                && SEC_TYPE_STOCK.equals(stock.getSecType());   // 개별주만(순위 API와 동일 — ETF 제외)
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

    /** $1 등 USD 금액을 매매기준율(mid)로 원화 환산 — 무상주의 원화 취득원가 기록용(회계). 캐시 미스면 야후 폴백, 둘 다 비면 502. */
    private int usdToKrw(BigDecimal usd) {
        BigDecimal rate = currencyRateProvider.current().exchangeRate();
        // 잘못된 환율로 원화 취득원가가 0/음수로 박히는 것 방지(currentPrice와 동일 정책). multiply라 divide와 달리 0도 안 터짐.
        if (rate == null || rate.signum() <= 0) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환율 조회 실패");
        }
        return usd.multiply(rate).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** 보유 적립 — 기존 있으면 수량 합산·가중평균, 없으면 신규. */
    /** 보상 적립 — 매수와 동일한 보유 원자 upsert(수량·평단·원화원가). 동시성 안전. */
    private void upsertHolding(Long userId, Long accountId, String stockCode, String currency,
                              BigDecimal quantity, BigDecimal grantPrice, BigDecimal krwAmount) {
        // 웰컴 보상(소수 무상주, <1) → 소수점 보유로 적립. fractionalDelta=quantity(즉시 floor 전환).
        holdingMapper.upsertBuy(userId, accountId, stockCode, quantity, grantPrice, krwAmount, currency, quantity);
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
