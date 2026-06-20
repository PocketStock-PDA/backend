package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.kis.KisAskingPriceResponse;
import com.pocketstock.ledger.kis.KisMarketClient;
import com.pocketstock.ledger.trading.client.LsMarketClient;
import com.pocketstock.ledger.trading.client.LsT8450Response;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.OrderHistoryResponse;
import com.pocketstock.ledger.trading.dto.WholeOrderRequest;
import com.pocketstock.ledger.trading.dto.WholeOrderResponse;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.support.OverseasExchangeCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;

/**
 * 온주(정수 주식) 매수/매도. 호가 기반 지정가/시장가를 자체 시뮬로 즉시 전량 체결한다.
 * 소수점의 차수·배치·배분 머신을 거치지 않고 orders + holdings + deposit에 직접 반영.
 * 국내(KOSPI/KOSDAQ·KRW·LS 호가)·해외(NASDAQ/NYSE/AMEX·USD·KIS 호가)를 거래소로 분기한다.
 * ※ 수수료·세금 미반영(MVP). 해외 USD 위탁예수금은 이미 충전돼 있다고 가정(CMA↔위탁·자동환전은 후속).
 */
@Service
@RequiredArgsConstructor
public class WholeOrderService {

    /** securities_accounts.market (계좌 단위: DOMESTIC/OVERSEAS) */
    private static final String ACCOUNT_DOMESTIC = "DOMESTIC";
    private static final String ACCOUNT_OVERSEAS = "OVERSEAS";
    private static final String CURRENCY_KRW = "KRW";
    private static final String CURRENCY_USD = "USD";
    /** orders/tradable_stocks.exchange 는 거래소 단위 — composite FK 대상 */
    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");
    private static final int AVG_SCALE = 4;  // holdings.avg_buy_price DECIMAL(18,4)

    private final StockMapper stockMapper;
    private final SecuritiesAccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final HoldingMapper holdingMapper;
    private final DepositService depositService;
    private final LsMarketClient lsMarketClient;
    private final KisMarketClient kisMarketClient;

    /** 온주 매수/매도 — 검증 → 주문기록 → 자체 시뮬 체결 → holdings·예수금 반영. */
    @Transactional
    public WholeOrderResponse placeWholeOrder(Long userId, WholeOrderRequest req) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String side = normalize(req.side());
        String type = normalize(req.orderType());
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "side는 BUY 또는 SELL이어야 합니다.");
        }
        if (!"LIMIT".equals(type) && !"MARKET".equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "orderType은 LIMIT 또는 MARKET이어야 합니다.");
        }
        if (req.quantity() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1주 이상이어야 합니다.");
        }

        TradableStock stock = stockMapper.findByCode(req.stockCode());
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + req.stockCode());
        }
        boolean overseas = OVERSEAS_EXCHANGES.contains(stock.getExchange());
        if (!overseas && !DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 거래소: " + stock.getExchange());
        }
        String accountMarket = overseas ? ACCOUNT_OVERSEAS : ACCOUNT_DOMESTIC;
        String currency = overseas ? CURRENCY_USD : CURRENCY_KRW;
        // 불변식: 거래소에서 파생한 통화 == 종목마스터 통화. 어긋나면 마스터 데이터/매핑 불일치(서버 오류).
        if (!currency.equals(stock.getCurrency())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "종목 통화 불일치: " + stock.getStockCode() + " 거래소=" + stock.getExchange()
                            + "(→" + currency + ") vs 마스터=" + stock.getCurrency());
        }

        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, accountMarket);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    (overseas ? "해외" : "국내") + " 위탁계좌가 없습니다. 먼저 계좌를 개설하세요.");
        }

        BigDecimal fillPrice = resolveFillPrice(stock, overseas, side, type, req.price());
        BigDecimal quantity = BigDecimal.valueOf(req.quantity());
        BigDecimal totalAmount = fillPrice.multiply(quantity);

        Order order = Order.builder()
                .clientOrderId(UUID.randomUUID().toString())
                .userId(userId)
                .accountId(account.getId())
                .stockCode(req.stockCode())
                .exchange(stock.getExchange())   // 거래소값(KOSPI 등) — composite FK 정합
                .side(side)
                .orderType(type)
                .orderQuantity(quantity)
                .price(fillPrice)
                .status("RECEIVED")
                .source("MANUAL")
                .currency(currency)
                .requestedAt(LocalDateTime.now())
                .build();
        orderMapper.insert(order);

        BigDecimal balanceAfter;
        if ("BUY".equals(side)) {
            // 예수금 차감 먼저 — 원자 갱신이 음수 가드로 잔액부족을 막는다(INSUFFICIENT_BALANCE).
            balanceAfter = depositService.record(userId, account.getId(), "BUY",
                    totalAmount.negate(), currency, "order", order.getId());
            applyBuy(userId, account.getId(), req.stockCode(), quantity, fillPrice, currency);
        } else {
            applySell(account.getId(), req.stockCode(), quantity);
            balanceAfter = depositService.record(userId, account.getId(), "SELL",
                    totalAmount, currency, "order", order.getId());
        }

        orderMapper.updateFill(order.getId(), "FILLED", fillPrice);

        return new WholeOrderResponse(order.getId(), req.stockCode(), side, req.quantity(),
                fillPrice, totalAmount, "FILLED", balanceAfter);
    }

    /** 거래내역(최신순). */
    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> getOrderHistory(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return orderMapper.findByUserId(userId).stream()
                .map(o -> new OrderHistoryResponse(o.getId(), o.getStockCode(), o.getSide(),
                        o.getOrderType(), o.getOrderQuantity(), o.getPrice(), o.getStatus(), o.getCreatedAt()))
                .toList();
    }

    // ---- 체결 시뮬 ----

    /** 지정가=요청가, 시장가=최우선 호가. 국내=LS t8450, 해외=KIS 현재가호가. */
    private BigDecimal resolveFillPrice(TradableStock stock, boolean overseas, String side, String type,
                                        BigDecimal reqPrice) {
        if ("LIMIT".equals(type)) {
            if (reqPrice == null || reqPrice.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "지정가(price)를 입력해주세요.");
            }
            return reqPrice;
        }
        // MARKET — 매수는 최우선 매도호가, 매도는 최우선 매수호가로 체결
        return overseas ? overseasMarketPrice(stock, side) : domesticMarketPrice(stock.getStockCode(), side);
    }

    /** 국내 시장가 — LS 통합 호가(t8450) 최우선가, 비면 현재가로 대체. */
    private BigDecimal domesticMarketPrice(String stockCode, String side) {
        LsT8450Response.OutBlock ob = lsMarketClient.getDomesticOrderbook(stockCode);
        long best = "BUY".equals(side) ? ob.askPrices()[0] : ob.bidPrices()[0];
        if (best <= 0) {
            best = ob.price();  // 호가가 비어있으면 현재가로 대체
        }
        if (best <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시장가 체결가를 산정할 수 없습니다.");
        }
        return BigDecimal.valueOf(best);
    }

    /** 해외 시장가 — KIS 현재가호가(HHDFS76200100) 최우선가(소수점). */
    private BigDecimal overseasMarketPrice(TradableStock stock, String side) {
        String excd = OverseasExchangeCode.of(stock);
        KisAskingPriceResponse.Output2 o2 =
                kisMarketClient.getOverseasOrderbook(excd, stock.getStockCode()).output2();
        BigDecimal best = "BUY".equals(side) ? dec(o2.askPrices()[0]) : dec(o2.bidPrices()[0]);
        if (best.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시장가 체결가를 산정할 수 없습니다.");
        }
        return best;
    }

    private void applyBuy(Long userId, Long accountId, String stockCode, BigDecimal qty, BigDecimal fillPrice,
                          String currency) {
        Holding holding = holdingMapper.findByAccountAndStock(accountId, stockCode);
        if (holding == null) {
            holdingMapper.insert(Holding.builder()
                    .userId(userId)
                    .accountId(accountId)
                    .stockCode(stockCode)
                    .quantity(qty)
                    .avgBuyPrice(fillPrice)
                    .currency(currency)
                    .build());
            return;
        }
        BigDecimal newQty = holding.getQuantity().add(qty);
        // 평단 가중평균 = (기존수량×기존평단 + 매수수량×체결가) / 총수량
        BigDecimal newAvg = holding.getQuantity().multiply(holding.getAvgBuyPrice())
                .add(qty.multiply(fillPrice))
                .divide(newQty, AVG_SCALE, RoundingMode.HALF_UP);
        holdingMapper.updateQuantityAndAvg(holding.getId(), newQty, newAvg);
    }

    private void applySell(Long accountId, String stockCode, BigDecimal qty) {
        Holding holding = holdingMapper.findByAccountAndStock(accountId, stockCode);
        if (holding == null || holding.getQuantity().compareTo(qty) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "보유 수량이 부족합니다.");
        }
        // 매도는 평단 유지, 전량매도 시 quantity=0으로 row 보존(삭제 안 함)
        holdingMapper.updateQuantityAndAvg(holding.getId(),
                holding.getQuantity().subtract(qty), holding.getAvgBuyPrice());
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
