package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.firm.service.OperatingCashService;
import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.OrderStatus;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse;
import com.pocketstock.ledger.trading.dto.OrderCancelResponse;
import com.pocketstock.ledger.trading.dto.OrderHistoryResponse;
import com.pocketstock.ledger.trading.dto.OrderbookResponse;
import com.pocketstock.ledger.trading.dto.WholeOrderRequest;
import com.pocketstock.ledger.trading.dto.WholeOrderResponse;
import com.pocketstock.ledger.trading.matching.PendingOrderClosedEvent;
import com.pocketstock.ledger.trading.matching.PendingOrderCreatedEvent;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.support.BookWalker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

    private final StockMapper stockMapper;
    private final SecuritiesAccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final HoldingMapper holdingMapper;
    private final DepositService depositService;
    private final OperatingCashService operatingCashService;
    private final OperatingInventoryService operatingInventoryService;
    private final OrderRejectionService rejectionService;
    private final OrderbookService orderbookService;
    private final CurrencyRateProvider currencyRateProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderFundingService fundingService;

    /** 온주 매수/매도 — 검증 → 주문기록 → 자체 시뮬 체결 → holdings·예수금 반영. 직접주문=MANUAL. */
    @Transactional
    public WholeOrderResponse placeWholeOrder(Long userId, WholeOrderRequest req) {
        return placeWholeOrder(userId, req, "MANUAL");
    }

    /** source 지정 — 소수점 split·자동모으기가 출처(MANUAL/AUTO)를 박아 재사용. orders.source에 기록. */
    @Transactional
    public WholeOrderResponse placeWholeOrder(Long userId, WholeOrderRequest req, String source) {
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
        String clientOrderId = req.clientOrderId() == null ? "" : req.clientOrderId().trim();
        if (clientOrderId.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "멱등키(clientOrderId)가 필요합니다.");
        }
        // 멱등 단락: 같은 키의 주문이 이미 있으면 재체결 없이 기존 결과 반환(따닥 탭·재전송 방어).
        Order existing = orderMapper.findByClientOrderId(clientOrderId);
        if (existing != null) {
            // client_order_id는 전역 UNIQUE — 다른 유저 키 충돌이면 남의 주문 노출 금지(409).
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 사용된 멱등키입니다.");
            }
            return toResponse(existing);
        }

        // 거래 인증(비번)은 진입 컨트롤러(OrderController·FractionalOrderController)에서 1회 처리한다(#174).
        // 소수점이 정수부로 이 메서드를 재사용하므로, 여기 두면 인증이 정수부 유무로 들쭉날쭉해진다 — 진입점 게이트로 통일.
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

        // 종목·계좌가 해석된 이후의 비즈니스 실패는 REJECTED로 감사 기록(H3). 단 멱등 충돌은 제외.
        try {
            BigDecimal quantity = BigDecimal.valueOf(req.quantity());
            // 호가 사다리로 즉시체결 여부 판정(H4): 시장가·지정가 즉시분 → 체결가, 지정가 미체결 → PENDING.
            Execution exec = resolveExecution(stock, overseas, side, type, req.price(), quantity);
            boolean fillNow = exec.filled();
            // 즉시체결은 산정가, PENDING은 지정가(reqPrice)를 주문가로 기록.
            BigDecimal orderPrice = fillNow ? exec.price() : req.price();

            Order order = Order.builder()
                    .clientOrderId(clientOrderId)   // 클라 발급 멱등키(FIX clOrdID)
                    .userId(userId)
                    .accountId(account.getId())
                    .stockCode(req.stockCode())
                    .exchange(stock.getExchange())   // 거래소값(KOSPI 등) — composite FK 정합
                    .side(side)
                    .orderType(type)
                    .orderQuantity(quantity)
                    .price(orderPrice)
                    .status(fillNow ? OrderStatus.RECEIVED : OrderStatus.PENDING)
                    .source(source)
                    .currency(currency)
                    .requestedAt(LocalDateTime.now())
                    .build();
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                // 동시 중복 요청 — 거의 동시에 같은 키 2건이 단락을 통과한 경우. client_order_id UNIQUE가
                // 두 번째 INSERT를 막는다(중복 체결·차감 차단). 이 트랜잭션은 롤백, 재요청 시 위 단락이 처리.
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 주문입니다.");
            }
            String idemKey = "order:" + order.getId();   // 하위 원장 결정적 멱등키

            // 지정가 미체결 → PENDING: 자금/수량 hold만(원장 이동 없음). 체결은 매칭 데몬(H4 #92)이 수행.
            if (!fillNow) {
                if ("BUY".equals(side)) {
                    // PENDING 매수 hold(예수금 잠금)도 예수금을 요구 → CMA풀에서 명목(지정가×수량)만큼 선충당(#174).
                    // 취소·가격개선 시 안 쓴 충당분은 revertUnusedFunding으로 CMA에 반납된다.
                    fundingService.transferForBuy(userId, account.getId(), currency,
                            req.price().multiply(quantity), order.getId());
                }
                BigDecimal holdTotal = reserveForPending(side, account.getId(), req.stockCode(), quantity, req.price());
                // 커밋 후 매칭 엔진이 인덱스 등록·호가 구독 ON(롤백되면 발행돼도 AFTER_COMMIT이 안 탐).
                eventPublisher.publishEvent(new PendingOrderCreatedEvent(order.getId(), userId, account.getId(),
                        req.stockCode(), stock.getExchange(), side, req.price(), quantity, currency));
                return new WholeOrderResponse(order.getId(), req.stockCode(), side, req.quantity(),
                        req.price(), holdTotal, OrderStatus.PENDING.name(),
                        depositService.getBalance(account.getId()));
            }

            BigDecimal fillPrice = exec.price();
            BigDecimal totalAmount = fillPrice.multiply(quantity);
            BigDecimal balanceAfter;
            if ("BUY".equals(side)) {
                // CMA풀 → 예수금 자동충당(#174): 주문가능 부족분만 BUY_TRANSFER로 끌어온다. 같은 트랜잭션이라
                // 이후 체결이 실패하면 충당까지 통째 롤백(역이체 불필요). 풀도 부족하면 INSUFFICIENT_BALANCE.
                fundingService.transferForBuy(userId, account.getId(), currency, totalAmount, order.getId());
                // 예수금 차감 — 원자 갱신이 음수 가드로 잔액부족을 막는다(INSUFFICIENT_BALANCE).
                balanceAfter = depositService.record(userId, account.getId(), "BUY",
                        totalAmount.negate(), currency, "order", order.getId(), idemKey);
                // 복식부기 상대 leg(H1): 유저 출금의 짝으로 회사 현금 수취(+). 같은 로컬 트랜잭션.
                operatingCashService.record("BUY", totalAmount, currency, "order", order.getId(), idemKey);
                // 원화 취득원가 = 국내는 체결금액 그대로, 해외는 체결 시점 실시간 환율로 환산.
                BigDecimal krwAmount = overseas ? totalAmount.multiply(fxRateForKrwBasis()) : totalAmount;
                applyBuy(userId, account.getId(), req.stockCode(), quantity, fillPrice, krwAmount, currency);
                // 복식부기 주식 leg: 유저 holdings 증가의 짝으로 회사 옴니버스 재고 −qty. 같은 로컬 트랜잭션.
                operatingInventoryService.record(req.stockCode(), -quantity.intValueExact());
            } else {
                applySell(account.getId(), req.stockCode(), quantity);
                depositService.record(userId, account.getId(), "SELL",
                        totalAmount, currency, "order", order.getId(), idemKey);
                // 복식부기 상대 leg(H1): 유저 입금의 짝으로 회사 현금 지급(−). 같은 로컬 트랜잭션.
                operatingCashService.record("SELL", totalAmount.negate(), currency, "order", order.getId(), idemKey);
                // 복식부기 주식 leg: 유저 holdings 감소의 짝으로 회사 옴니버스 재고 +qty.
                operatingInventoryService.record(req.stockCode(), quantity.intValueExact());
                // 매도대금 즉시 환류(#174, A안): 예수금 → CMA풀(SELL_RETURN). 예수금에 매도대금 잔류 0. 같은 트랜잭션.
                fundingService.returnFromSell(userId, account.getId(), currency, totalAmount, order.getId());
                balanceAfter = depositService.getBalance(account.getId());   // 환류 후 예수금(매도대금 빠진 값)
            }

            // 전이 가드 ②: RECEIVED → FILLED 조건부 전이(같은 tx라 항상 1행, 0이면 정합성 오류).
            if (orderMapper.transitionFill(order.getId(), OrderStatus.RECEIVED, OrderStatus.FILLED, fillPrice) == 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "주문 상태 전이 실패(RECEIVED→FILLED)");
            }

            return new WholeOrderResponse(order.getId(), req.stockCode(), side, req.quantity(),
                    fillPrice, totalAmount, OrderStatus.FILLED.name(), balanceAfter);
        } catch (BusinessException e) {
            // 멱등 충돌은 '실패한 주문'이 아니라 중복 — REJECTED 기록 제외. 그 외 비즈니스 실패만 감사 기록.
            if (e.getErrorCode() != ErrorCode.IDEMPOTENCY_CONFLICT) {
                try {
                    // 자금 이동은 본 tx 롤백으로 환원됨. REJECTED 행만 별도 tx(REQUIRES_NEW)로 commit.
                    rejectionService.recordRejection(userId, account.getId(), req.stockCode(), stock.getExchange(),
                            side, type, BigDecimal.valueOf(req.quantity()), req.price(), currency, e.getMessage());
                } catch (Exception ignore) {
                    // 감사 기록 실패가 원 비즈니스 예외를 가리지 않도록 무시.
                }
            }
            throw e;
        }
    }

    /** 주문 취소 — 소수점 QUEUED / 온주 PENDING만 취소(→CANCELLED). 종결 상태면 409. */
    @Transactional
    public OrderCancelResponse cancelOrder(Long userId, Long orderId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Order o = orderMapper.findByIdAndUserId(orderId, userId);
        if (o == null) {
            // 없는 주문 또는 타인 주문 — 존재 노출 없이 404.
            throw new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        if (!o.getStatus().isCancellable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        OrderStatus prev = o.getStatus();
        // 전이 가드 ②: QUEUED/PENDING일 때만 CANCELLED. 0행이면 그 사이 체결/취소된 경합 → 409.
        if (orderMapper.cancelIfCancellable(orderId, userId) == 0) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        // hold 환원(같은 tx) — 경로별 분기. 온주 PENDING / 소수점 QUEUED(전송 전 취소, FRAC-008) 모두 처리.
        if (prev == OrderStatus.PENDING) {
            // 온주 PENDING(M2 hold) — 매수=예수금 hold(지정가×수량) 해제, 매도=수량 hold 해제.
            if ("BUY".equals(o.getSide())) {
                depositService.releaseHold(o.getAccountId(), o.getPrice().multiply(o.getOrderQuantity()));
                // 충당분 반납(#174): hold 풀려 주문가능으로 돌아온 충당분을 CMA로 sweep(REVERT). 같은 트랜잭션.
                fundingService.revertUnusedFunding(userId, o.getAccountId(), o.getCurrency(), o.getId(), "cancel");
            } else {
                holdingMapper.releaseWholeReserve(o.getAccountId(), o.getStockCode(), o.getOrderQuantity());
            }
            // 커밋 후 매칭 엔진이 인덱스 제거·그 종목 PENDING 0건이면 호가 구독 OFF.
            eventPublisher.publishEvent(new PendingOrderClosedEvent(orderId, o.getStockCode(), o.getExchange()));
        } else if (prev == OrderStatus.QUEUED) {
            // 소수점 QUEUED — 접수 hold 환원. 매수=잠근 KRW(held_amount), 매도=잠근 수량(order_quantity).
            // (소수점은 차수·배치 데몬 대상이라 PendingOrderClosedEvent 발행 안 함 — 온주 매칭 인덱스와 무관.)
            if ("BUY".equals(o.getSide())) {
                if (o.getHeldAmount() != null) {
                    depositService.releaseHold(o.getAccountId(), o.getHeldAmount());
                }
            } else {
                holdingMapper.releaseFractionalReserve(o.getAccountId(), o.getStockCode(), o.getOrderQuantity());
            }
        }
        return new OrderCancelResponse(orderId, OrderStatus.CANCELLED.name());
    }

    /** 거래내역(최신순). */
    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> getOrderHistory(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return orderMapper.findByUserId(userId).stream()
                .map(o -> new OrderHistoryResponse(o.getId(), o.getStockCode(), o.getSide(),
                        o.getOrderType(), o.getOrderQuantity(), o.getOrderAmount(), o.getPrice(),
                        o.getStatus().name(), o.getCurrency(), o.getCreatedAt()))
                .toList();
    }

    /** 미체결 주문(최신순) — 온주 PENDING + 소수점 QUEUED, 종목 무관 전체. */
    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> getPendingOrders(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return orderMapper.findActiveByUserId(userId).stream()
                .map(o -> new OrderHistoryResponse(o.getId(), o.getStockCode(), o.getSide(),
                        o.getOrderType(), o.getOrderQuantity(), o.getOrderAmount(), o.getPrice(),
                        o.getStatus().name(), o.getCurrency(), o.getCreatedAt()))
                .toList();
    }

    /** 멱등 재요청 응답 — 기존 주문을 그대로 반환. balanceAfter는 현재 예수금(재체결 없음). */
    private WholeOrderResponse toResponse(Order o) {
        BigDecimal total = o.getPrice().multiply(o.getOrderQuantity());
        BigDecimal balanceAfter = depositService.getBalance(o.getAccountId());
        return new WholeOrderResponse(o.getId(), o.getStockCode(), o.getSide(),
                o.getOrderQuantity().longValueExact(), o.getPrice(), total, o.getStatus().name(), balanceAfter);
    }

    // ---- 체결 시뮬 ----

    /**
     * 체결 계획 산정(H4) — 호가 사다리로 평가해 "즉시 체결(가격)" 또는 "PENDING"으로 분기.
     * 시장가: 사다리 훑어 전량 가능하면 가중평균가로 즉시 체결, 잔량 부족이면 거부(부분체결 없음).
     * 지정가: 지정가 이내 호가만 훑어 전량 가능하면 그 가중평균가로 즉시 체결(가격개선), 아니면 PENDING.
     */
    private Execution resolveExecution(TradableStock stock, boolean overseas, String side, String type,
                                       BigDecimal reqPrice, BigDecimal quantity) {
        boolean buy = "BUY".equals(side);
        Book book = fetchBook(stock, overseas, buy);
        if ("MARKET".equals(type)) {
            BookWalker.Fill f = BookWalker.walk(book.prices(), book.volumes(), quantity, null, buy);
            if (!f.complete()) {
                // 국내 호가창이 통째로 비었으면(휴장·데이터 없음) 현재가 단일가로 대체 — 기존 폴백 유지.
                if (!overseas && book.prices()[0].signum() <= 0 && book.current().signum() > 0) {
                    return new Execution(true, book.current());
                }
                throw new BusinessException(ErrorCode.INVALID_INPUT, "시장가로 전량 체결할 호가 잔량이 부족합니다.");
            }
            // 가격제한폭(국내): 체결 평균가가 상·하한가를 벗어나면 호가 비정상 → 거부. 해외는 무제한.
            if (!overseas) {
                checkPriceBand(f.avgPrice(), book.upperLimit(), book.lowerLimit());
            }
            return new Execution(true, f.avgPrice());
        }
        // LIMIT — 지정가 이내(매수=지정가 이하·매도=지정가 이상) 호가만 훑는다.
        if (reqPrice == null || reqPrice.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지정가(price)를 입력해주세요.");
        }
        BookWalker.Fill f = BookWalker.walk(book.prices(), book.volumes(), quantity, reqPrice, buy);
        if (f.complete()) {
            return new Execution(true, f.avgPrice());   // 지정가 이내 전량 체결(가격개선 가능)
        }
        return new Execution(false, null);              // 미체결 → PENDING(매칭 데몬 H4 #92가 체결)
    }

    /**
     * 거래소별 호가창(한 방향) — 스냅샷 백업(#145): 장중엔 실시간, 장외·빈호가창엔 마지막 캡처된
     * 호가창을 동결가로 반환한다(REST→WS로 받던 마지막 시장 상태 동결). 매수=매도호가/매도=매수호가
     * + (국내) 현재가·상하한가. 동결 호가창을 그대로 사다리 훑기 → 장중/장외 분기 없음.
     */
    private Book fetchBook(TradableStock stock, boolean overseas, boolean buy) {
        if (overseas) {
            ForeignQuoteResponse q = orderbookService.overseasSnapshot(stock);
            List<ForeignQuoteResponse.Level> levels = buy ? q.asks() : q.bids();
            return new Book(
                    levels.stream().map(ForeignQuoteResponse.Level::price).toArray(BigDecimal[]::new),
                    levels.stream().map(ForeignQuoteResponse.Level::volume).toArray(BigDecimal[]::new),
                    BigDecimal.ZERO, 0L, 0L);
        }
        OrderbookResponse ob = orderbookService.domesticSnapshot(stock.getStockCode());
        List<OrderbookResponse.Level> levels = buy ? ob.asks() : ob.bids();
        return new Book(
                levels.stream().map(OrderbookResponse.Level::price).toArray(BigDecimal[]::new),
                levels.stream().map(OrderbookResponse.Level::volume).toArray(BigDecimal[]::new),
                ob.currentPrice(),
                ob.upperLimit() == null ? 0L : ob.upperLimit().longValue(),
                ob.lowerLimit() == null ? 0L : ob.lowerLimit().longValue());
    }

    /**
     * 지정가 PENDING 진입 hold(M2) — 매수=예수금 묶기, 매도=보유수량 묶기. 부족하면 거부(→REJECTED).
     * @return 묶인 명목금액(지정가×수량) — 응답 표시용.
     */
    private BigDecimal reserveForPending(String side, Long accountId, String stockCode,
                                         BigDecimal quantity, BigDecimal limitPrice) {
        BigDecimal notional = limitPrice.multiply(quantity);
        if ("BUY".equals(side)) {
            depositService.hold(accountId, notional);   // 주문가능 부족이면 INSUFFICIENT_BALANCE
        } else if (holdingMapper.reserveWholeForSell(accountId, stockCode, quantity) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "온주 매도가능 수량이 부족합니다.");
        }
        return notional;
    }

    /** 체결 계획: 즉시 체결(filled=true, price)이거나 PENDING(filled=false). */
    private record Execution(boolean filled, BigDecimal price) {
    }

    /** 호가창 스냅샷(한 방향). 국내만 current/상하한가 채움(해외는 0). */
    private record Book(BigDecimal[] prices, BigDecimal[] volumes, BigDecimal current,
                        long upperLimit, long lowerLimit) {
    }

    /** 체결 평균가가 상·하한가 범위를 벗어나면 거부(상·하한가 0이면 미제공으로 보고 통과). */
    private void checkPriceBand(BigDecimal price, long upperLimit, long lowerLimit) {
        if (upperLimit > 0 && price.compareTo(BigDecimal.valueOf(upperLimit)) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "체결가가 상한가를 초과합니다.");
        }
        if (lowerLimit > 0 && price.compareTo(BigDecimal.valueOf(lowerLimit)) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "체결가가 하한가 미만입니다.");
        }
    }

    /** 매수 — 보유 원자 upsert(수량 누적 + 평단 가중평균 + 원화원가 누적). 동시 매수 lost update 차단. */
    private void applyBuy(Long userId, Long accountId, String stockCode, BigDecimal qty, BigDecimal fillPrice,
                          BigDecimal krwAmount, String currency) {
        // 온주 매수 — fractionalDelta=0(소수분 없음, 직접소유 정수재고).
        holdingMapper.upsertBuy(userId, accountId, stockCode, qty, fillPrice, krwAmount, currency, BigDecimal.ZERO);
    }

    /** 해외 매수 원화원가 환산용 — 체결 시점 실시간 매매기준율(USD/KRW). 캐시 미스면 야후 폴백, 둘 다 비면 502. */
    private BigDecimal fxRateForKrwBasis() {
        return currencyRateProvider.current().exchangeRate();
    }

    /** 온주 매도 — 보유 수량 원자 차감(온주 매도가능 가드). 전량매도 시 quantity=0으로 row 보존. */
    private void applySell(Long accountId, String stockCode, BigDecimal qty) {
        if (holdingMapper.reduceWholeForSell(accountId, stockCode, qty) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "온주 보유 수량이 부족합니다.");
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
