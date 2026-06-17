package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 온주(정수 주식) 매수/매도. 호가 기반 지정가/시장가를 자체 시뮬로 즉시 전량 체결한다.
 * 소수점의 차수·배치·배분 머신을 거치지 않고 orders + holdings + deposit에 직접 반영.
 * ※ 현재 국내(KRW)만. 수수료·세금 미반영(MVP).
 */
@Service
@RequiredArgsConstructor
public class WholeOrderService {

    /** securities_accounts.market (계좌 단위: DOMESTIC/OVERSEAS) */
    private static final String ACCOUNT_DOMESTIC = "DOMESTIC";
    private static final String CURRENCY_KRW = "KRW";
    /** orders/tradable_stocks.exchange 는 거래소 단위(KOSPI/KOSDAQ) — composite FK 대상 */
    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final int AVG_SCALE = 4;  // holdings.avg_buy_price DECIMAL(18,4)

    private final StockMapper stockMapper;
    private final SecuritiesAccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final HoldingMapper holdingMapper;
    private final DepositService depositService;
    private final LsMarketClient lsMarketClient;

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
        if (!DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "온주 매매는 현재 국내만 지원합니다(해외 추후).");
        }

        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, ACCOUNT_DOMESTIC);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "국내 위탁계좌가 없습니다. 먼저 계좌를 개설하세요.");
        }

        BigDecimal fillPrice = resolveFillPrice(req.stockCode(), side, type, req.price());
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
                .currency(CURRENCY_KRW)
                .requestedAt(LocalDateTime.now())
                .build();
        orderMapper.insert(order);

        BigDecimal balanceAfter;
        if ("BUY".equals(side)) {
            if (depositService.getKrwBalance(userId).compareTo(totalAmount) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "예수금이 부족합니다.");
            }
            applyBuy(userId, account.getId(), req.stockCode(), quantity, fillPrice);
            balanceAfter = depositService.record(userId, account.getId(), "BUY",
                    totalAmount.negate(), CURRENCY_KRW, "order", order.getId());
        } else {
            applySell(account.getId(), req.stockCode(), quantity);
            balanceAfter = depositService.record(userId, account.getId(), "SELL",
                    totalAmount, CURRENCY_KRW, "order", order.getId());
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

    /** 지정가=요청가, 시장가=최우선 호가(없으면 현재가). */
    private BigDecimal resolveFillPrice(String stockCode, String side, String type, BigDecimal reqPrice) {
        if ("LIMIT".equals(type)) {
            if (reqPrice == null || reqPrice.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "지정가(price)를 입력해주세요.");
            }
            return reqPrice;
        }
        // MARKET — 매수는 최우선 매도호가, 매도는 최우선 매수호가로 체결
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

    private void applyBuy(Long userId, Long accountId, String stockCode, BigDecimal qty, BigDecimal fillPrice) {
        Holding holding = holdingMapper.findByAccountAndStock(accountId, stockCode);
        if (holding == null) {
            holdingMapper.insert(Holding.builder()
                    .userId(userId)
                    .accountId(accountId)
                    .stockCode(stockCode)
                    .quantity(qty)
                    .avgBuyPrice(fillPrice)
                    .currency(CURRENCY_KRW)
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
