package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.OrderStatus;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.domain.TradingRound;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.FractionalOrderResponse;
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
 * <p>D4: <b>국내 먼저</b>. 해외(USD 충전경로·자동환전·환율 콜드스타트)는 후속(#155 해외 확장)으로 게이트한다.
 */
@Service
@RequiredArgsConstructor
public class FractionalOrderService {

    private static final String ACCOUNT_DOMESTIC = "DOMESTIC";
    private static final String CURRENCY_KRW = "KRW";
    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");

    /** 국내 최소 주문금액·금액단위(천원). 수량매수 버퍼 1%(해외 2%, 해외는 후속). */
    private static final BigDecimal MIN_ORDER_KRW = BigDecimal.valueOf(1_000);
    private static final BigDecimal KRW_UNIT = BigDecimal.valueOf(1_000);
    private static final BigDecimal BUFFER_DOMESTIC = new BigDecimal("0.01");
    private static final int QTY_SCALE = 6;   // 내부원장 주수 6자리(DECIMAL(18,6))

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
        SplitOrderResponse replay = findSplitReplay(userId, ctx);
        if (replay != null) {
            return replay;
        }
        try {
            BigDecimal estPrice = domesticEstPrice(ctx.stock().getStockCode(), true);
            return "QUANTITY".equals(method)
                    ? splitBuyByQuantity(userId, ctx, req, estPrice)
                    : splitBuyByAmount(userId, ctx, req, estPrice);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.IDEMPOTENCY_CONFLICT) {
                try {
                    rejectionService.recordRejection(userId, ctx.account().getId(), ctx.stock().getStockCode(),
                            ctx.stock().getExchange(), "BUY", method, null, null, CURRENCY_KRW, e.getMessage());
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
        if (estPrice.multiply(qty).compareTo(MIN_ORDER_KRW) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예상 주문금액이 최소 1,000원 이상이어야 합니다.");
        }
        int whole = qty.setScale(0, RoundingMode.FLOOR).intValueExact();
        BigDecimal frac = qty.subtract(BigDecimal.valueOf(whole));

        WholeOrderResponse w = whole >= 1 ? placeWholeBuy(userId, ctx, whole) : null;
        FracEnq f = null;
        if (frac.signum() > 0) {
            // 소수분 hold = 예상금액×(1+버퍼). split 잔여라 표준 최소주문 검증은 생략(총주문에서 이미 검증).
            BigDecimal hold = estPrice.multiply(frac).multiply(BigDecimal.ONE.add(BUFFER_DOMESTIC))
                    .setScale(4, RoundingMode.UP);
            f = enqueueFracBuy(userId, ctx, "QUANTITY", null, frac, frac, hold);
        }
        return buildSplit(ctx, w, f);
    }

    /** 금액매수 split — whole=floor(amount/현재가)는 온주로(실대금 차감), 남은 금액은 소수 금액매수로. */
    private SplitOrderResponse splitBuyByAmount(Long userId, Ctx ctx, FractionalOrderRequest req, BigDecimal estPrice) {
        BigDecimal amount = req.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주문금액(amount)을 입력해주세요.");
        }
        if (amount.compareTo(MIN_ORDER_KRW) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "최소 주문금액은 1,000원입니다.");
        }
        if (amount.remainder(KRW_UNIT).signum() != 0) {
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
        return buildSplit(ctx, w, f);
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
        TradingRound round = currentRound(ACCOUNT_DOMESTIC);
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
                .currency(CURRENCY_KRW)
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
    private SplitOrderResponse buildSplit(Ctx ctx, WholeOrderResponse w, FracEnq f) {
        BigDecimal orderable = depositService.getBalance(ctx.account().getId());
        return new SplitOrderResponse(ctx.stock().getStockCode(), "BUY",
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
    private SplitOrderResponse findSplitReplay(Long userId, Ctx ctx) {
        Order wo = orderMapper.findByClientOrderId(ctx.clientOrderId() + ":W");
        Order fo = orderMapper.findByClientOrderId(ctx.clientOrderId() + ":F");
        if (wo == null && fo == null) {
            return null;
        }
        if ((wo != null && !wo.getUserId().equals(userId)) || (fo != null && !fo.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 사용된 멱등키입니다.");
        }
        BigDecimal orderable = depositService.getBalance(ctx.account().getId());
        return new SplitOrderResponse(ctx.stock().getStockCode(), "BUY",
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

    /** 소수점 매도 — method=AMOUNT(금액) | ALL(전량). */
    @Transactional
    public FractionalOrderResponse placeSell(Long userId, FractionalOrderRequest req) {
        return place(userId, req, "SELL");
    }

    private FractionalOrderResponse place(Long userId, FractionalOrderRequest req, String side) {
        Ctx ctx = resolveContext(userId, req, side);
        String clientOrderId = ctx.clientOrderId();
        TradableStock stock = ctx.stock();
        SecuritiesAccount account = ctx.account();
        String method = normalize(req.orderType());

        // 멱등 단락 — 매도는 단일 주문이라 base 키(매수 split은 :W/:F 서브키, findSplitReplay가 처리).
        Order existing = orderMapper.findByClientOrderId(clientOrderId);
        if (existing != null) {
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 사용된 멱등키입니다.");
            }
            return toResponse(existing);
        }

        // 종목·계좌 해석 이후의 비즈니스 실패는 REJECTED로 감사 기록(H3). 멱등 충돌은 제외.
        try {
            // 접수 시점 예상가 — 매도=최우선 매수호가, 폴백 현재가.
            BigDecimal estPrice = domesticEstPrice(stock.getStockCode(), "BUY".equals(side));
            Reserve r = "BUY".equals(side)
                    ? reserveBuy(account.getId(), method, req, estPrice)
                    : reserveSell(account.getId(), stock.getStockCode(), method, req, estPrice);

            TradingRound round = currentRound(ACCOUNT_DOMESTIC);

            Order order = Order.builder()
                    .clientOrderId(clientOrderId)
                    .userId(userId)
                    .accountId(account.getId())
                    .stockCode(stock.getStockCode())
                    .exchange(stock.getExchange())
                    .side(side)
                    .orderType(method)                 // 소수점: AMOUNT | QUANTITY | ALL
                    .orderAmount(r.orderAmount())
                    .orderQuantity(r.orderQuantity())
                    .estQuantity(r.estQuantity())
                    .heldAmount(r.heldAmount())         // 매수=잠근 KRW / 매도=NULL(수량은 holdings.held_fractional)
                    .status(OrderStatus.QUEUED)         // 접수 즉시 차수 대기(전송 전 취소 가능)
                    .source("MANUAL")
                    .roundId(round.getId())
                    .currency(CURRENCY_KRW)
                    .requestedAt(LocalDateTime.now())
                    .build();
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                // 거의 동시에 같은 키 2건 — UNIQUE가 두 번째를 막아 hold도 함께 롤백. 재요청 시 멱등 단락이 처리.
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 주문입니다.");
            }

            BigDecimal orderable = "BUY".equals(side)
                    ? depositService.getBalance(account.getId()) // 표시용(잔액 — held는 별도)
                    : null;
            return new FractionalOrderResponse(order.getId(), round.getId(), stock.getStockCode(),
                    side, method, r.estQuantity(), r.heldAmount(), OrderStatus.QUEUED.name(), orderable);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.IDEMPOTENCY_CONFLICT) {
                try {
                    rejectionService.recordRejection(userId, account.getId(), stock.getStockCode(),
                            stock.getExchange(), side, method, null, null, CURRENCY_KRW, e.getMessage());
                } catch (Exception ignore) {
                    // 감사 기록 실패가 원 예외를 가리지 않게 무시.
                }
            }
            throw e;
        }
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
        if (OVERSEAS_EXCHANGES.contains(stock.getExchange())) {
            // D4: 국내 먼저 — 해외는 USD 충전경로·자동환전·환율 콜드스타트 가드 선결(#155 해외 확장).
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해외 소수점 주문은 후속 지원입니다(국내 먼저).");
        }
        if (!DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 거래소: " + stock.getExchange());
        }
        if (Boolean.FALSE.equals(stock.getIsActive())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "거래 정지 종목입니다: " + stock.getStockCode());
        }
        if (!CURRENCY_KRW.equals(stock.getCurrency())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "종목 통화 불일치: " + stock.getStockCode() + " 마스터=" + stock.getCurrency());
        }
        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, ACCOUNT_DOMESTIC);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "국내 위탁계좌가 없습니다. 먼저 계좌를 개설하세요.");
        }
        return new Ctx(clientOrderId, stock, account);
    }

    /** 검증 통과 컨텍스트. */
    private record Ctx(String clientOrderId, TradableStock stock, SecuritiesAccount account) {
    }

    // ---- 자금/수량 hold ----

    /**
     * 매수 hold(D1) — AMOUNT는 주문금액 그대로(돈이 상한, 미수 불가 → 버퍼X), QUANTITY는 예상금액×(1+버퍼)
     * (수량 고정·금액 변동 → 가격상승 미수 방지). 실제 잠근 KRW를 held_amount로 반환.
     */
    private Reserve reserveBuy(Long accountId, String method, FractionalOrderRequest req, BigDecimal estPrice) {
        if ("AMOUNT".equals(method)) {
            BigDecimal amount = req.amount();
            if (amount == null || amount.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "주문금액(amount)을 입력해주세요.");
            }
            if (amount.compareTo(MIN_ORDER_KRW) < 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "최소 주문금액은 1,000원입니다.");
            }
            if (amount.remainder(KRW_UNIT).signum() != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "주문금액은 1,000원 단위입니다.");
            }
            depositService.hold(accountId, amount);   // 주문가능 부족이면 INSUFFICIENT_BALANCE
            BigDecimal est = amount.divide(estPrice, QTY_SCALE, RoundingMode.DOWN);
            return new Reserve(amount, null, est, amount);
        }
        // QUANTITY 수량매수 — 예상금액×(1+버퍼) 잠금. 체결 후 미사용분은 환원(배치 집행 #153).
        BigDecimal qty = req.quantity();
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주문수량(quantity)을 입력해주세요.");
        }
        qty = qty.setScale(QTY_SCALE, RoundingMode.DOWN);
        BigDecimal estNotional = estPrice.multiply(qty);
        if (estNotional.compareTo(MIN_ORDER_KRW) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예상 주문금액이 최소 1,000원 이상이어야 합니다.");
        }
        BigDecimal hold = estNotional.multiply(BigDecimal.ONE.add(BUFFER_DOMESTIC))
                .setScale(4, RoundingMode.UP);
        depositService.hold(accountId, hold);
        return new Reserve(null, qty, qty, hold);
    }

    /**
     * 매도 hold — 소수 보유수량 잠금(holdings.held_fractional). ALL=소수 매도가능 전량, AMOUNT=예상가 환산 주수.
     * held_amount는 NULL(매도 hold 기준은 수량). 잠근 주수를 order_quantity로 기록(환원·집행 기준).
     */
    private Reserve reserveSell(Long accountId, String stockCode, String method,
                                FractionalOrderRequest req, BigDecimal estPrice) {
        // 소수 매도가능 = fractional_qty − held (온주 분은 제외 — 온주→소수 분할 금지, FRAC-010 #157).
        BigDecimal available = holdingMapper.findAvailableFractional(accountId, stockCode);
        if (available == null || available.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매도가능 소수점 보유가 없습니다(온주는 온주 매도로).");
        }
        BigDecimal qty;
        BigDecimal orderAmount = null;
        if ("ALL".equals(method)) {
            qty = available.setScale(QTY_SCALE, RoundingMode.DOWN);
        } else {   // AMOUNT 금액매도 — 예상가로 주수 환산(상한=매도가능 전량)
            BigDecimal amount = req.amount();
            if (amount == null || amount.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "매도금액(amount)을 입력해주세요.");
            }
            orderAmount = amount;
            qty = amount.divide(estPrice, QTY_SCALE, RoundingMode.DOWN);
            if (qty.compareTo(available) > 0) {
                // 소수 매도가능(0.5)을 넘는 금액 → 거부(온주 0.3 떼기 불가). 온주 분은 온주 매도로.
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "매도금액이 소수점 보유(" + available + "주)를 초과합니다. 온주 분은 온주 매도로 처리하세요.");
            }
        }
        if (qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매도 수량이 0입니다.");
        }
        // 소수 수량 hold(소수 매도가능 가드) — 소수부 초과·경합이면 0행 → 거부.
        if (holdingMapper.reserveFractionalForSell(accountId, stockCode, qty) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소수 매도가능 수량이 변경되었습니다. 다시 시도해주세요.");
        }
        return new Reserve(orderAmount, qty, qty, null);
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

    /** 국내 예상가 — 매수=최우선 매도호가, 매도=최우선 매수호가, 폴백 현재가. 셋 다 없으면 시세 미수신(502). */
    private BigDecimal domesticEstPrice(String stockCode, boolean buy) {
        OrderbookResponse ob = orderbookService.domesticSnapshot(stockCode);
        BigDecimal best = firstPrice(buy ? ob.asks() : ob.bids());
        if (best != null && best.signum() > 0) {
            return best;
        }
        if (ob.currentPrice() != null && ob.currentPrice().signum() > 0) {
            return ob.currentPrice();
        }
        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "시세 정보를 아직 받지 못했습니다.");
    }

    private static BigDecimal firstPrice(List<OrderbookResponse.Level> levels) {
        return (levels == null || levels.isEmpty()) ? null : levels.get(0).price();
    }

    private void validateMethod(String side, String method) {
        if ("BUY".equals(side)) {
            if (!"AMOUNT".equals(method) && !"QUANTITY".equals(method)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "매수 method는 AMOUNT 또는 QUANTITY입니다.");
            }
        } else if (!"AMOUNT".equals(method) && !"ALL".equals(method)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매도 method는 AMOUNT 또는 ALL입니다.");
        }
    }

    /** 멱등 재요청 응답 — 기존 주문 그대로. */
    private FractionalOrderResponse toResponse(Order o) {
        BigDecimal orderable = "BUY".equals(o.getSide()) ? depositService.getBalance(o.getAccountId()) : null;
        return new FractionalOrderResponse(o.getId(), o.getRoundId(), o.getStockCode(), o.getSide(),
                o.getOrderType(), o.getEstQuantity(), o.getHeldAmount(), o.getStatus().name(), orderable);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    /** 접수 시 확정된 주문 금액/수량 + 잠근 자금(hold) 묶음. */
    private record Reserve(BigDecimal orderAmount, BigDecimal orderQuantity,
                           BigDecimal estQuantity, BigDecimal heldAmount) {
    }
}
