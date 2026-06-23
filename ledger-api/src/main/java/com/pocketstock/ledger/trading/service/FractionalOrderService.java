package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.OrderStatus;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.domain.TradingRound;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.OrderbookResponse;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.dto.WholeOrderRequest;
import com.pocketstock.ledger.trading.dto.WholeOrderResponse;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.mapper.RoundMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 소수점 매수/매도 접수 — 검증 → 자금/수량 hold → 현재 1분 차수에 QUEUED로 편입(비동기).
 * 즉시 체결하는 온주({@link WholeOrderService})와 흐름이 근본적으로 달라(접수와 체결이 차수로 분리)
 * 서비스를 분리한다. 단 {@code orders} 테이블·{@code Order} 도메인·예수금/보유 토대는 온주와 공용(ERD-04 §08b).
 *
 * <p>체결(상계·회사 선부담 ceil·시뮬·비례배분·정산)은 스케줄러(#152)+배치 집행기(#153)가 수행한다.
 * 여긴 입구만 — 응답은 즉시 QUEUED, 예상수량은 참고치(확정은 allocations).
 *
 * <p>국내(KRW·LS 호가)·해외(USD·KIS 호가)를 거래소로 분기({@link MarketSpec}). 해외는 OVERSEAS 위탁계좌의
 * USD 예수금 충전 전제(자동환전 제외, #155). 환율 콜드스타트 가드는 배치 집행(스케줄러)에서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class FractionalOrderService {

    private static final String ACCOUNT_DOMESTIC = "DOMESTIC";
    private static final String ACCOUNT_OVERSEAS = "OVERSEAS";
    private static final String CURRENCY_KRW = "KRW";
    private static final String CURRENCY_USD = "USD";
    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");

    /** 국내 최소 주문금액·금액단위(천원), 수량매수 버퍼 1%. 해외는 최소 $0.01·단위 없음·버퍼 2%. */
    private static final BigDecimal MIN_ORDER_KRW = BigDecimal.valueOf(1_000);
    private static final BigDecimal KRW_UNIT = BigDecimal.valueOf(1_000);
    private static final BigDecimal BUFFER_DOMESTIC = new BigDecimal("0.01");
    private static final BigDecimal MIN_ORDER_USD = new BigDecimal("0.01");
    private static final BigDecimal BUFFER_OVERSEAS = new BigDecimal("0.02");
    private static final int QTY_SCALE = 6;   // 내부원장 주수 6자리(DECIMAL(18,6))

    /** 국내 위탁계좌(KRW·천원단위·버퍼 1%). */
    private static final MarketSpec SPEC_DOMESTIC =
            new MarketSpec(false, ACCOUNT_DOMESTIC, CURRENCY_KRW, MIN_ORDER_KRW, KRW_UNIT, BUFFER_DOMESTIC);
    /** 해외 위탁계좌(USD·단위 없음·버퍼 2%). 자동환전 제외 — USD 예수금 충전 전제(#155). */
    private static final MarketSpec SPEC_OVERSEAS =
            new MarketSpec(true, ACCOUNT_OVERSEAS, CURRENCY_USD, MIN_ORDER_USD, null, BUFFER_OVERSEAS);

    private static final DateTimeFormatter ROUND_NO = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StockMapper stockMapper;
    private final SecuritiesAccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final RoundMapper roundMapper;
    private final HoldingMapper holdingMapper;
    private final DepositService depositService;
    private final OrderbookService orderbookService;
    private final OrderRejectionService rejectionService;
    private final WholeOrderService wholeOrderService;

    /**
     * 소수점 매수 — split 라우터(FRAC-010 #157). 정수부=온주 즉시 호가체결 / 소수부=소수 차수 배치로 쪼갠다.
     * 한 트랜잭션이라 둘 다 성공 or 둘 다 롤백(부분 실패 없음). 13.14주→온주13+소수0.14, 0.1→소수만, 1.0→온주만.
     * 프론트는 split을 모르고 "13.14 매수" 한 번만 보낸다.
     */
    @Transactional
    public SplitOrderResponse placeBuy(Long userId, FractionalOrderRequest req) {
        Ctx ctx = resolveContext(userId, req, "BUY");
        String method = normalize(req.orderType());
        // 멱등 replay — 서브키(:W/:F) 중 하나라도 있으면 이미 처리됨 → 재구성 반환(한 tx라 둘 다 있거나 둘 다 없음).
        SplitOrderResponse replay = findSplitReplay("BUY", userId, ctx);
        if (replay != null) {
            return replay;
        }
        try {
            BigDecimal estPrice = estPrice(ctx, true);
            return "QUANTITY".equals(method)
                    ? splitBuyByQuantity(userId, ctx, req, estPrice)
                    : splitBuyByAmount(userId, ctx, req, estPrice);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.IDEMPOTENCY_CONFLICT) {
                try {
                    rejectionService.recordRejection(userId, ctx.account().getId(), ctx.stock().getStockCode(),
                            ctx.stock().getExchange(), "BUY", method, null, null, ctx.spec().currency(), e.getMessage());
                } catch (Exception ignore) {
                    // 감사 기록 실패가 원 예외를 가리지 않게 무시.
                }
            }
            throw e;
        }
    }

    // ---- 매수 split (온주 즉시체결 + 소수 차수배치, 한 tx) ----

    /** 수량매수 split — whole=floor(qty)는 온주 MARKET 즉시체결, frac=qty−whole는 소수 차수배치. */
    private SplitOrderResponse splitBuyByQuantity(Long userId, Ctx ctx, FractionalOrderRequest req, BigDecimal estPrice) {
        BigDecimal qty = req.quantity();
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주문수량(quantity)을 입력해주세요.");
        }
        qty = qty.setScale(QTY_SCALE, RoundingMode.DOWN);
        MarketSpec spec = ctx.spec();
        if (estPrice.multiply(qty).compareTo(spec.minOrder()) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예상 주문금액이 최소 " + minOrderText(spec) + " 이상이어야 합니다.");
        }
        int whole = qty.setScale(0, RoundingMode.FLOOR).intValueExact();
        BigDecimal frac = qty.subtract(BigDecimal.valueOf(whole));

        WholeOrderResponse w = whole >= 1 ? placeWholeBuy(userId, ctx, whole) : null;
        FracEnq f = null;
        if (frac.signum() > 0) {
            // 소수분 hold = 예상금액×(1+버퍼). split 잔여라 표준 최소주문 검증은 생략(총주문에서 이미 검증).
            BigDecimal hold = estPrice.multiply(frac).multiply(BigDecimal.ONE.add(spec.buffer()))
                    .setScale(4, RoundingMode.UP);
            f = enqueueFracBuy(userId, ctx, "QUANTITY", null, frac, frac, hold);
        }
        return buildSplit("BUY", ctx, w, f);
    }

    /** 금액매수 split — whole=floor(amount/현재가)는 온주로(실대금 차감), 남은 금액은 소수 금액매수로. */
    private SplitOrderResponse splitBuyByAmount(Long userId, Ctx ctx, FractionalOrderRequest req, BigDecimal estPrice) {
        BigDecimal amount = req.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주문금액(amount)을 입력해주세요.");
        }
        MarketSpec spec = ctx.spec();
        if (amount.compareTo(spec.minOrder()) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "최소 주문금액은 " + minOrderText(spec) + "입니다.");
        }
        // 금액단위 제약은 국내(천원단위)만 — 해외는 단위 없음(spec.unit == null).
        if (spec.unit() != null && amount.remainder(spec.unit()).signum() != 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주문금액은 1,000원 단위입니다.");
        }
        int whole = amount.divide(estPrice, 0, RoundingMode.DOWN).intValueExact();

        WholeOrderResponse w = null;
        BigDecimal remaining = amount;
        if (whole >= 1) {
            w = placeWholeBuy(userId, ctx, whole);
            remaining = amount.subtract(w.totalAmount());   // 온주 실체결대금 차감 → 남은 예산
        }
        FracEnq f = null;
        if (remaining.signum() > 0) {
            BigDecimal estQty = remaining.divide(estPrice, QTY_SCALE, RoundingMode.DOWN);
            // 금액매수는 돈이 상한이라 버퍼X — 남은 금액 그대로 hold.
            f = enqueueFracBuy(userId, ctx, "AMOUNT", remaining, null, estQty, remaining);
        }
        return buildSplit("BUY", ctx, w, f);
    }

    /** 정수부 온주 MARKET 매수 — 같은 tx에 합류(즉시 호가체결). 멱등키 서브키 :W. */
    private WholeOrderResponse placeWholeBuy(Long userId, Ctx ctx, int wholeShares) {
        WholeOrderRequest wreq = new WholeOrderRequest(ctx.clientOrderId() + ":W",
                ctx.stock().getStockCode(), "BUY", "MARKET", null, wholeShares);
        return wholeOrderService.placeWholeOrder(userId, wreq);
    }

    /** 소수분 접수 — 예수금 hold + 현재 차수 QUEUED 편입. 멱등키 서브키 :F. */
    private FracEnq enqueueFracBuy(Long userId, Ctx ctx, String method, BigDecimal orderAmount,
                                   BigDecimal orderQuantity, BigDecimal estQty, BigDecimal hold) {
        depositService.hold(ctx.account().getId(), hold);
        TradingRound round = currentRound(ctx.spec().accountMarket());
        Order order = Order.builder()
                .clientOrderId(ctx.clientOrderId() + ":F")
                .userId(userId)
                .accountId(ctx.account().getId())
                .stockCode(ctx.stock().getStockCode())
                .exchange(ctx.stock().getExchange())
                .side("BUY")
                .orderType(method)
                .orderAmount(orderAmount)
                .orderQuantity(orderQuantity)
                .estQuantity(estQty)
                .heldAmount(hold)
                .status(OrderStatus.QUEUED)
                .source("MANUAL")
                .roundId(round.getId())
                .currency(ctx.spec().currency())
                .requestedAt(LocalDateTime.now())
                .build();
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 주문입니다.");
        }
        return new FracEnq(order.getId(), round.getId(), estQty, hold);
    }

    /** split 결과 합성. */
    private SplitOrderResponse buildSplit(String side, Ctx ctx, WholeOrderResponse w, FracEnq f) {
        BigDecimal orderable = depositService.getBalance(ctx.account().getId());
        return new SplitOrderResponse(ctx.stock().getStockCode(), side,
                w == null ? null : w.orderId(),
                w == null ? null : w.quantity(),
                w == null ? null : w.fillPrice(),
                w == null ? null : w.totalAmount(),
                f == null ? null : f.orderId(),
                f == null ? null : f.roundId(),
                f == null ? null : f.estQty(),
                f == null ? null : f.held(),
                f == null ? null : OrderStatus.QUEUED.name(),
                orderable);
    }

    /** 멱등 replay 재구성 — 서브키(:W/:F) 주문이 이미 있으면 그 결과로 합성. 없으면 null. */
    private SplitOrderResponse findSplitReplay(String side, Long userId, Ctx ctx) {
        Order wo = orderMapper.findByClientOrderId(ctx.clientOrderId() + ":W");
        Order fo = orderMapper.findByClientOrderId(ctx.clientOrderId() + ":F");
        if (wo == null && fo == null) {
            return null;
        }
        if ((wo != null && !wo.getUserId().equals(userId)) || (fo != null && !fo.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 사용된 멱등키입니다.");
        }
        BigDecimal orderable = depositService.getBalance(ctx.account().getId());
        return new SplitOrderResponse(ctx.stock().getStockCode(), side,
                wo == null ? null : wo.getId(),
                wo == null ? null : (wo.getOrderQuantity() == null ? null : wo.getOrderQuantity().longValueExact()),
                wo == null ? null : wo.getPrice(),
                wo == null ? null : (wo.getPrice() == null ? null : wo.getPrice().multiply(wo.getOrderQuantity())),
                fo == null ? null : fo.getId(),
                fo == null ? null : fo.getRoundId(),
                fo == null ? null : fo.getEstQuantity(),
                fo == null ? null : fo.getHeldAmount(),
                fo == null ? null : fo.getStatus().name(),
                orderable);
    }

    private record FracEnq(Long orderId, Long roundId, BigDecimal estQty, BigDecimal held) {
    }

    /**
     * 소수점 매도 — split 라우터(FRAC-010 #157). 정수부=온주 MARKET 즉시매도 / 소수부=소수 차수매도로 쪼갠다.
     * method=QUANTITY(수량)|AMOUNT(금액)|ALL(전량). 소수부가 소수 보유 초과면 거부(온주→소수 분할 불가).
     * 한 트랜잭션이라 둘 다 성공 or 둘 다 롤백.
     */
    @Transactional
    public SplitOrderResponse placeSell(Long userId, FractionalOrderRequest req) {
        Ctx ctx = resolveContext(userId, req, "SELL");
        String method = normalize(req.orderType());
        SplitOrderResponse replay = findSplitReplay("SELL", userId, ctx);
        if (replay != null) {
            return replay;
        }
        try {
            return splitSell(userId, ctx, req, method);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.IDEMPOTENCY_CONFLICT) {
                try {
                    rejectionService.recordRejection(userId, ctx.account().getId(), ctx.stock().getStockCode(),
                            ctx.stock().getExchange(), "SELL", method, null, null, ctx.spec().currency(), e.getMessage());
                } catch (Exception ignore) {
                    // 감사 기록 실패가 원 예외를 가리지 않게 무시.
                }
            }
            throw e;
        }
    }

    /**
     * 매도 split — 총 매도수량 산정 → whole=min(floor(수량), 온주가용)는 온주 매도, frac=수량−whole는 소수 매도.
     * 소수부는 소수 매도가능 이하만(온주→소수 분할 금지). 온주부 초과는 온주 엔진이 거부. ALL/QUANTITY/AMOUNT 통일.
     */
    private SplitOrderResponse splitSell(Long userId, Ctx ctx, FractionalOrderRequest req, String method) {
        Long accountId = ctx.account().getId();
        String stockCode = ctx.stock().getStockCode();
        BigDecimal estPrice = estPrice(ctx, false);   // 매도=최우선 매수호가
        BigDecimal wholeAvail = orZero(holdingMapper.findAvailableWhole(accountId, stockCode));
        BigDecimal fracAvail = orZero(holdingMapper.findAvailableFractional(accountId, stockCode));

        BigDecimal orderAmount = null;
        BigDecimal sellQty;
        if ("ALL".equals(method)) {
            sellQty = wholeAvail.add(fracAvail);            // 전량 = 온주 매도가능 + 소수 매도가능
        } else if ("QUANTITY".equals(method)) {
            sellQty = req.quantity();
            if (sellQty == null || sellQty.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "주문수량(quantity)을 입력해주세요.");
            }
        } else {   // AMOUNT 금액매도 — 예상가로 주수 환산
            orderAmount = req.amount();
            if (orderAmount == null || orderAmount.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "매도금액(amount)을 입력해주세요.");
            }
            sellQty = orderAmount.divide(estPrice, QTY_SCALE, RoundingMode.DOWN);
        }
        sellQty = sellQty.setScale(QTY_SCALE, RoundingMode.DOWN);
        if (sellQty.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매도할 수량이 없습니다(보유 없음).");
        }
        // 온주 매도는 가용 정수까지만 — 나머지는 소수로(온주가 모자라면 소수에서 더 떼지 않음).
        int whole = Math.min(sellQty.setScale(0, RoundingMode.FLOOR).intValueExact(), wholeAvail.intValue());
        BigDecimal frac = sellQty.subtract(BigDecimal.valueOf(whole));
        if (frac.compareTo(fracAvail) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "소수 매도가능(" + fracAvail + "주)을 초과합니다 — 온주 분은 정수로만 매도됩니다.");
        }

        WholeOrderResponse w = whole >= 1 ? placeWholeSell(userId, ctx, whole) : null;
        FracEnq f = frac.signum() > 0 ? enqueueFracSell(userId, ctx, method, orderAmount, frac) : null;
        if (w == null && f == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매도할 수량이 없습니다.");
        }
        return buildSplit("SELL", ctx, w, f);
    }

    /** 정수부 온주 MARKET 매도 — 같은 tx 합류(즉시 호가체결). 서브키 :W. */
    private WholeOrderResponse placeWholeSell(Long userId, Ctx ctx, int whole) {
        WholeOrderRequest wreq = new WholeOrderRequest(ctx.clientOrderId() + ":W",
                ctx.stock().getStockCode(), "SELL", "MARKET", null, whole);
        return wholeOrderService.placeWholeOrder(userId, wreq);
    }

    /** 소수부 매도 접수 — 소수 수량 hold(held_fractional) + 현재 차수 QUEUED 편입. 서브키 :F. */
    private FracEnq enqueueFracSell(Long userId, Ctx ctx, String method, BigDecimal orderAmount, BigDecimal fracQty) {
        if (holdingMapper.reserveFractionalForSell(ctx.account().getId(), ctx.stock().getStockCode(), fracQty) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소수 매도가능 수량이 변경되었습니다. 다시 시도해주세요.");
        }
        TradingRound round = currentRound(ctx.spec().accountMarket());
        Order order = Order.builder()
                .clientOrderId(ctx.clientOrderId() + ":F")
                .userId(userId)
                .accountId(ctx.account().getId())
                .stockCode(ctx.stock().getStockCode())
                .exchange(ctx.stock().getExchange())
                .side("SELL")
                .orderType(method)
                .orderAmount(orderAmount)
                .orderQuantity(fracQty)
                .estQuantity(fracQty)
                .heldAmount(null)                  // 매도 hold 기준은 수량(holdings.held_fractional)
                .status(OrderStatus.QUEUED)
                .source("MANUAL")
                .roundId(round.getId())
                .currency(ctx.spec().currency())
                .requestedAt(LocalDateTime.now())
                .build();
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 주문입니다.");
        }
        return new FracEnq(order.getId(), round.getId(), fracQty, null);
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 공통 검증·해석 — userId·method·멱등키 비어있음·종목(국내·거래가능·통화)·계좌. 멱등 단락은 호출자별로. */
    private Ctx resolveContext(Long userId, FractionalOrderRequest req, String side) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validateMethod(side, normalize(req.orderType()));
        String clientOrderId = req.clientOrderId() == null ? "" : req.clientOrderId().trim();
        if (clientOrderId.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "멱등키(clientOrderId)가 필요합니다.");
        }
        TradableStock stock = stockMapper.findByCode(req.stockCode());
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + req.stockCode());
        }
        // 거래소로 시장(국내/해외) 판별 → 통화·계좌·최소주문·버퍼 규격을 결정. 해외는 USD·OVERSEAS 계좌(#155).
        MarketSpec spec;
        if (DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            spec = SPEC_DOMESTIC;
        } else if (OVERSEAS_EXCHANGES.contains(stock.getExchange())) {
            spec = SPEC_OVERSEAS;
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 거래소: " + stock.getExchange());
        }
        if (Boolean.FALSE.equals(stock.getIsActive())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "거래 정지 종목입니다: " + stock.getStockCode());
        }
        // 불변식: 거래소에서 파생한 통화 == 종목마스터 통화. 어긋나면 마스터 데이터 불일치(서버 오류).
        if (!spec.currency().equals(stock.getCurrency())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "종목 통화 불일치: " + stock.getStockCode() + " 거래소=" + stock.getExchange()
                            + "(→" + spec.currency() + ") vs 마스터=" + stock.getCurrency());
        }
        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, spec.accountMarket());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    (spec.overseas() ? "해외" : "국내") + " 위탁계좌가 없습니다. 먼저 계좌를 개설하세요.");
        }
        return new Ctx(clientOrderId, stock, account, spec);
    }

    /** 검증 통과 컨텍스트. */
    private record Ctx(String clientOrderId, TradableStock stock, SecuritiesAccount account, MarketSpec spec) {
    }

    /**
     * 시장 규격 — 국내/해외 접수 분기값을 한 곳에 모은다.
     * @param unit 금액단위(국내 천원=1,000), 해외는 단위 없음(null).
     */
    private record MarketSpec(boolean overseas, String accountMarket, String currency,
                             BigDecimal minOrder, BigDecimal unit, BigDecimal buffer) {
    }

    // ---- 차수 ----

    /** 현재 분(UTC) 차수 find-or-create — 없으면 만들고, 동시 접수가 만든 기존 행이면 그 id로 수렴. */
    private TradingRound currentRound(String market) {
        LocalDateTime minuteStart = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime execAt = minuteStart.plusMinutes(1);
        LocalDate tradeDate = minuteStart.toLocalDate();
        TradingRound round = TradingRound.builder()
                .market(market)
                .roundNo(minuteStart.format(ROUND_NO))
                .tradeDate(tradeDate)
                .submitOpen(minuteStart)
                .submitClose(execAt)
                .executeAt(execAt)
                .settleAt(execAt)
                .cancelDeadline(execAt)
                .pricingMethod("MIXED")   // 실제 가격은 batch_orders별(DOMESTIC_TICK/MARKET)
                .status("OPEN")
                .build();
        roundMapper.upsertCurrent(round);   // round.id 채워짐(신규/기존 무관)
        return round;
    }

    // ---- 보조 ----

    /**
     * 예상가(접수용 hold 산정) — 매수=최우선 매도호가, 매도=최우선 매수호가, 폴백 현재가. 시장으로 호가소스 분기.
     * 국내=LS 스냅샷, 해외=KIS 스냅샷(동결 폴백 포함, #145). 호가·현재가 모두 없으면 시세 미수신(502).
     */
    private BigDecimal estPrice(Ctx ctx, boolean buy) {
        if (ctx.spec().overseas()) {
            ForeignQuoteResponse q = orderbookService.overseasSnapshot(ctx.stock());
            BigDecimal best = firstForeignPrice(buy ? q.asks() : q.bids());
            if (best != null && best.signum() > 0) {
                return best;
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "시세 정보를 아직 받지 못했습니다.");
        }
        OrderbookResponse ob = orderbookService.domesticSnapshot(ctx.stock().getStockCode());
        BigDecimal best = firstPrice(buy ? ob.asks() : ob.bids());
        if (best != null && best.signum() > 0) {
            return best;
        }
        if (ob.currentPrice() != null && ob.currentPrice().signum() > 0) {
            return ob.currentPrice();
        }
        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "시세 정보를 아직 받지 못했습니다.");
    }

    /** 최소주문 안내 문구 — 국내 "1,000원" / 해외 "$0.01". */
    private static String minOrderText(MarketSpec spec) {
        return spec.overseas() ? "$" + spec.minOrder().toPlainString() : "1,000원";
    }

    private static BigDecimal firstPrice(List<OrderbookResponse.Level> levels) {
        return (levels == null || levels.isEmpty()) ? null : levels.get(0).price();
    }

    private static BigDecimal firstForeignPrice(List<ForeignQuoteResponse.Level> levels) {
        return (levels == null || levels.isEmpty()) ? null : levels.get(0).price();
    }

    private void validateMethod(String side, String method) {
        if ("BUY".equals(side)) {
            if (!"AMOUNT".equals(method) && !"QUANTITY".equals(method)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "매수 method는 AMOUNT 또는 QUANTITY입니다.");
            }
        } else if (!"AMOUNT".equals(method) && !"QUANTITY".equals(method) && !"ALL".equals(method)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매도 method는 AMOUNT·QUANTITY·ALL 중 하나입니다.");
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
