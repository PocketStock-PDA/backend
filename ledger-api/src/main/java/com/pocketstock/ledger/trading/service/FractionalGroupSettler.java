package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.firm.service.OperatingCashService;
import com.pocketstock.ledger.trading.domain.Allocation;
import com.pocketstock.ledger.trading.domain.BatchOrder;
import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse;
import com.pocketstock.ledger.trading.dto.OrderbookResponse;
import com.pocketstock.ledger.trading.mapper.AllocationMapper;
import com.pocketstock.ledger.trading.mapper.BatchOrderMapper;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 소수점 배치 — <b>한 그룹(종목·side·가격모델) 1건의 정산(#153, 엔진 본체)을 한 트랜잭션으로</b> 처리한다.
 * 합산 → 회사 선부담(매수 ceil / 매도 floor) → 시뮬 체결 → 평균가 배분 → 고객·firm 정산 →
 * 0-sum 검증을 커밋 조건으로 둔다(#101 · ERD-04 §08, 현금식은 2026-06-23 정정판).
 *
 * <p><b>거울 모델</b>: 회사는 시장엔 온주(정수)로만 거래하고 끝수는 양방향으로 떠안는다.
 * 매수=ceil(순주−firm가용)·매도=floor(firm재고+순주). 총재고 항상 ≥0(음수가드).
 * <b>현금 2-leg</b>: 고객분(ref=allocation) + 시장분(ref=batch). Δcash = Σnet − whole×fill(매수) / whole×fill − Σnet(매도).
 *
 * <p>국내(KRW·LS)·해외(USD·KIS) 거래소 분기. 해외 취득원가는 체결 시점 환율로 KRW 환산(#155). 매수-매도 상계 없음(그룹별 독립). fee/tax=0(D6). 수량매수 자금부족분만 거부·제외(FRAC-015).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FractionalGroupSettler {

    private static final String CURRENCY_KRW = "KRW";
    private static final String CURRENCY_USD = "USD";
    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");
    private static final int QTY_SCALE = 6;
    private static final int TICK_STEPS = 5;   // 국내 금액매수 = 현재가 + 5틱(#101)

    private final OrderMapper orderMapper;
    private final BatchOrderMapper batchOrderMapper;
    private final AllocationMapper allocationMapper;
    private final HoldingMapper holdingMapper;
    private final StockMapper stockMapper;
    private final DepositService depositService;
    private final OperatingCashService operatingCashService;
    private final OperatingInventoryService operatingInventoryService;
    private final OrderbookService orderbookService;
    private final CurrencyRateProvider currencyRateProvider;
    private final OrderFundingService fundingService;

    /**
     * 한 그룹 정산(한 tx) — 같은 종목·side·가격모델의 QUEUED 주문을 합산·체결·배분·정산.
     * 시세 미수신·정합성 위반 시 예외 → 전체 롤백(돈 안 움직임). 호출자가 catch해 거부+환원.
     */
    @Transactional
    public void settleGroup(List<Order> orders, String stockCode, String exchange,
                            String side, String pricingMethod, Long roundId) {
        boolean buy = "BUY".equals(side);
        boolean overseas = OVERSEAS_EXCHANGES.contains(exchange);
        String currency = overseas ? CURRENCY_USD : CURRENCY_KRW;
        BigDecimal fillPrice = resolveFillPrice(stockCode, overseas, buy, pricingMethod);
        // 해외 매수만 취득원가(KRW) 환산용 환율 1회 확보 — 콜드스타트면 502(스케줄러가 선제 보류하나 2차 가드).
        BigDecimal fxRate = (buy && overseas) ? fxRateForKrwBasis() : null;

        // 1) 자금 가능분 선별 — 수량매수만 버퍼 초과 급등 시 부족 가능(FRAC-015). 금액매수·매도는 항상 가능.
        List<Funded> funded = new ArrayList<>();
        BigDecimal estTotalQty = BigDecimal.ZERO;
        for (Order o : orders) {
            BigDecimal qty = desiredQty(o, buy, fillPrice);
            if (qty.signum() <= 0) {
                rejectOne(o, "체결 수량 0");
                continue;
            }
            BigDecimal cost = qty.multiply(fillPrice);
            if (buy && o.getHeldAmount() != null && cost.compareTo(o.getHeldAmount()) > 0) {
                rejectOne(o, "예수금 부족(버퍼 초과 급등) — 합산 제외(FRAC-015)");
                continue;
            }
            funded.add(new Funded(o, qty, cost));
            estTotalQty = estTotalQty.add(qty);
        }
        if (funded.isEmpty()) {
            return;   // 전원 제외 — 시장 주문 없음
        }

        // 2) 회사 선부담 온주 수량 — 매수 ceil(순주−firm가용) / 매도 floor(firm재고+순주). 총재고 ≥0.
        BigDecimal firmAvail = operatingInventoryService.fractionalRemainder(stockCode);
        int wholeQty = buy
                ? Math.max(0, estTotalQty.subtract(firmAvail).setScale(0, RoundingMode.CEILING).intValueExact())
                : firmAvail.add(estTotalQty).setScale(0, RoundingMode.FLOOR).intValueExact();

        // 3) 블록주문 기록(시뮬 즉시 체결).
        LocalDateTime now = LocalDateTime.now();
        BatchOrder batch = BatchOrder.builder()
                .stockCode(stockCode).exchange(exchange).side(side).roundId(roundId)
                .pricingMethod(pricingMethod).netFractionalQty(estTotalQty).wholeQty(wholeQty)
                .status("SENT").fillPrice(fillPrice).sentAt(now)
                .build();
        batchOrderMapper.insert(batch);
        Long batchId = batch.getId();

        // 4) 시장 leg(현금) — 매수=firm 시장 지불(−), 매도=firm 시장 수취(+). whole_qty=0이면 시장거래 없음.
        if (wholeQty > 0) {
            BigDecimal market = fillPrice.multiply(BigDecimal.valueOf(wholeQty));
            operatingCashService.record(buy ? "SELL" : "BUY", buy ? market.negate() : market,
                    currency, "batch", batchId, "frac-batch:" + batchId);
        }

        // 5) 배분 + 고객 정산. 집행 중 취소된 주문(sendForBatch=0)은 건너뛰고 firm이 흡수(0-sum 유지).
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (Funded f : funded) {
            Order o = f.order();
            if (orderMapper.sendForBatch(o.getId(), batchId) == 0) {
                continue;   // 그 사이 취소됨 — 제외(취소가 hold 환원 처리). firm이 끝수로 흡수.
            }
            BigDecimal gross = f.qty().multiply(fillPrice);   // = net (fee/tax 0)
            Allocation alloc = Allocation.builder()
                    .orderId(o.getId()).batchOrderId(batchId)
                    .allocatedQty(f.qty()).allocatedPrice(fillPrice)
                    .grossAmount(gross).fee(BigDecimal.ZERO).tax(BigDecimal.ZERO).netAmount(gross)
                    .build();
            allocationMapper.insert(alloc);
            String idem = "frac-alloc:" + alloc.getId();
            if (buy) {
                // 접수 hold(held_amount) 해제 → 실대금만 차감(미사용 버퍼는 주문가능으로 환원).
                depositService.releaseHold(o.getAccountId(), o.getHeldAmount());
                depositService.record(o.getUserId(), o.getAccountId(), "BUY", gross.negate(),
                        currency, "allocation", alloc.getId(), idem);
                operatingCashService.record("BUY", gross, currency, "allocation", alloc.getId(), idem);
                // 취득원가(KRW) = 국내는 체결대금 그대로, 해외는 체결 시점 환율로 환산(온주와 동일 소스).
                BigDecimal krwAmount = overseas ? gross.multiply(fxRate) : gross;
                // 소수점 매수 → fractionalDelta=qty(즉시 floor 전환).
                holdingMapper.upsertBuy(o.getUserId(), o.getAccountId(), stockCode,
                        f.qty(), fillPrice, krwAmount, currency, f.qty());
                // 충당 버퍼 반납(#174): 정산 후 예수금 주문가능(held−gross 버퍼)을 CMA로 sweep(REVERT). 같은 tx.
                fundingService.revertUnusedFunding(o.getUserId(), o.getAccountId(), currency, o.getId(), "fracbuf");
            } else {
                // 접수 소수 수량 hold 해제 → 소수 보유 실인도(quantity·fractional_qty 동시 차감).
                holdingMapper.releaseFractionalReserve(o.getAccountId(), stockCode, f.qty());
                if (holdingMapper.reduceFractionalForSell(o.getAccountId(), stockCode, f.qty()) == 0) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "소수 매도 체결 수량 차감 실패(소수 잔고 부족)");
                }
                depositService.record(o.getUserId(), o.getAccountId(), "SELL", gross,
                        currency, "allocation", alloc.getId(), idem);
                operatingCashService.record("SELL", gross.negate(), currency, "allocation", alloc.getId(), idem);
                // 매도대금 즉시 환류(#174, A안): 예수금 → CMA풀(SELL_RETURN). 예수금 잔류 0. 같은 tx.
                fundingService.returnFromSell(o.getUserId(), o.getAccountId(), currency, gross, o.getId());
            }
            if (orderMapper.markFilledFractional(o.getId()) == 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "주문 전이 실패(SENT→FILLED) orderId=" + o.getId());
            }
            allocatedTotal = allocatedTotal.add(f.qty());
        }

        // 6) firm 끝수재고 양방향 정산 — 매수: +(whole−배분) 흡수 / 매도: +(배분−whole) 흡수. 음수가드.
        BigDecimal fracDelta = buy
                ? BigDecimal.valueOf(wholeQty).subtract(allocatedTotal)
                : allocatedTotal.subtract(BigDecimal.valueOf(wholeQty));
        operatingInventoryService.applyFractional(stockCode, fracDelta);

        // 7) 0-sum 주식 항등식 검증(커밋 조건) — 배분 + firm끝수 = 시장체결주수. 불일치면 롤백+알람.
        BigDecimal lhs = buy ? allocatedTotal.add(fracDelta) : allocatedTotal.subtract(fracDelta);
        if (lhs.subtract(BigDecimal.valueOf(wholeQty)).abs().compareTo(new BigDecimal("0.000001")) > 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "0-sum 주식 불변식 위반 stock=" + stockCode + " 배분=" + allocatedTotal
                            + " Δ끝수=" + fracDelta + " 체결주수=" + wholeQty);
        }

        // 8) 블록 체결 확정.
        batchOrderMapper.markFilled(batchId, fillPrice, LocalDateTime.now());
        log.info("[소수점배치] 정산 stock={} {} 배분={} 체결주수={} 끝수Δ={} @ {}",
                stockCode, side, allocatedTotal, wholeQty, fracDelta, fillPrice);
    }

    /**
     * 그룹 거부+자금원복(#154) — 정산 실패 시 호출자가 별도 tx로 부른다. 활성(QUEUED/SENT) 주문만
     * REJECTED + hold 환원(매수 예수금/매도 수량). 이미 취소·체결된 건은 가드가 무시(중복 환원 차단).
     */
    @Transactional
    public void rejectGroup(List<Order> orders, String reason) {
        for (Order o : orders) {
            try {
                // 멱등 가드: 활성(QUEUED/SENT)→REJECTED 전이에 성공한 호출만 환원. 0이면 이미 종결 → 중복 환원 차단.
                if (orderMapper.rejectActive(o.getId(), trim(reason)) == 0) {
                    continue;
                }
                refundHold(o);
            } catch (Exception e) {
                log.error("[소수점배치] 거부 환원 실패 orderId={} — 개별 스킵", o.getId(), e);
            }
        }
    }

    // ---- 보조 ----

    /** 접수 방식별 체결수량 — 금액=금액/체결가(6자리 절사) / 수량·전량=접수 시 확정 수량. */
    private BigDecimal desiredQty(Order o, boolean buy, BigDecimal fillPrice) {
        if (buy && "AMOUNT".equals(o.getOrderType())) {
            return o.getOrderAmount().divide(fillPrice, QTY_SCALE, RoundingMode.DOWN);
        }
        // QUANTITY 매수 / ALL·AMOUNT 매도 — 접수 시 잠근 수량(order_quantity)을 그대로 체결.
        return o.getOrderQuantity() == null ? BigDecimal.ZERO
                : o.getOrderQuantity().setScale(QTY_SCALE, RoundingMode.DOWN);
    }

    /** 한 주문 거부+환원(같은 tx) — 자금부족 제외·수량 0 등. 전이 성공한 경우만 환원(멱등). */
    private void rejectOne(Order o, String reason) {
        if (orderMapper.rejectActive(o.getId(), trim(reason)) == 0) {
            return;
        }
        refundHold(o);
    }

    /** 접수 hold 환원 — 매수=예수금 held_amount, 매도=잠근 수량(order_quantity). 거부·실패 보상 공용(FRAC-014). */
    private void refundHold(Order o) {
        if ("BUY".equals(o.getSide())) {
            if (o.getHeldAmount() != null) {
                depositService.releaseHold(o.getAccountId(), o.getHeldAmount());
                // 충당분 반납(#174): 거부로 풀린 예수금 주문가능(충당분)을 CMA로 sweep(REVERT). 같은 tx.
                fundingService.revertUnusedFunding(o.getUserId(), o.getAccountId(), o.getCurrency(), o.getId(), "fracreject");
            }
        } else {
            holdingMapper.releaseFractionalReserve(o.getAccountId(), o.getStockCode(), o.getOrderQuantity());
        }
    }

    /**
     * 체결가 — 해외=KIS 스냅샷 시장가(매수 best-ask·매도 best-bid, 동결 폴백 #145) /
     * 국내 금액매수=현재가+5틱(KRX 호가단위) / 국내 그 외=실행시점 시장가. 시세 없으면 502 → 그룹 거부.
     * 해외엔 DOMESTIC_TICK이 들어오지 않는다(가격모델 게이트, FractionalBatchService).
     */
    private BigDecimal resolveFillPrice(String stockCode, boolean overseas, boolean buy, String pricingMethod) {
        if (overseas) {
            TradableStock stock = stockMapper.findByCode(stockCode);
            if (stock == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
            }
            ForeignQuoteResponse q = orderbookService.overseasSnapshot(stock);
            BigDecimal best = positive(firstForeignPrice(buy ? q.asks() : q.bids()));
            if (best == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "체결가 산정 실패(시세 없음): " + stockCode);
            }
            return best;
        }
        OrderbookResponse ob = orderbookService.domesticSnapshot(stockCode);
        if ("DOMESTIC_TICK".equals(pricingMethod)) {
            BigDecimal base = positive(ob.currentPrice());
            if (base == null) {
                base = positive(firstPrice(ob.asks()));   // 현재가 공백이면 최우선 매도호가로 대체
            }
            if (base == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "체결가 산정 실패(시세 없음): " + stockCode);
            }
            return base.add(tickSize(base).multiply(BigDecimal.valueOf(TICK_STEPS)));
        }
        BigDecimal best = positive(buy ? firstPrice(ob.asks()) : firstPrice(ob.bids()));
        if (best == null) {
            best = positive(ob.currentPrice());
        }
        if (best == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "체결가 산정 실패(시세 없음): " + stockCode);
        }
        return best;
    }

    /** KRX 호가단위(2023 개정) — 국내 금액매수 +5틱 산정용. */
    private static BigDecimal tickSize(BigDecimal price) {
        long p = price.longValue();
        if (p < 2_000) return BigDecimal.ONE;
        if (p < 5_000) return BigDecimal.valueOf(5);
        if (p < 20_000) return BigDecimal.valueOf(10);
        if (p < 50_000) return BigDecimal.valueOf(50);
        if (p < 200_000) return BigDecimal.valueOf(100);
        if (p < 500_000) return BigDecimal.valueOf(500);
        return BigDecimal.valueOf(1_000);
    }

    /** 해외 매수 취득원가(KRW) 환산용 — 체결 시점 실시간 매매기준율(USD/KRW). 캐시 미스면 야후 폴백, 둘 다 비면 502(온주와 동일 가드). */
    private BigDecimal fxRateForKrwBasis() {
        return currencyRateProvider.current().exchangeRate();
    }

    private static BigDecimal firstPrice(List<OrderbookResponse.Level> levels) {
        return (levels == null || levels.isEmpty()) ? null : levels.get(0).price();
    }

    private static BigDecimal firstForeignPrice(List<ForeignQuoteResponse.Level> levels) {
        return (levels == null || levels.isEmpty()) ? null : levels.get(0).price();
    }

    private static BigDecimal positive(BigDecimal v) {
        return (v != null && v.signum() > 0) ? v : null;
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    /** 자금 검증 통과한 주문 + 확정 체결수량/대금. */
    private record Funded(Order order, BigDecimal qty, BigDecimal cost) {
    }
}
