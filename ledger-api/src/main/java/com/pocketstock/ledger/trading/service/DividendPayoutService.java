package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.trading.domain.DividendPayout;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.dto.DividendPayoutResponse;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.mapper.DividendPayoutMapper;
import com.pocketstock.ledger.trading.port.CmaPoolPort;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 배당 지급 엔진 — 보유자별로 {@code 보유수량 × 주당배당금}을 CMA 원화풀에 입금하고(항상), DRIP ON이면 재투자한다.
 * 세금·수수료 무시(시뮬). 국내(KRW)만. 스케줄러({@code DividendPayoutScheduler})가 종목·보유자를 돌며 호출한다.
 *
 * <p>지급과 재투자는 트랜잭션을 나눈다 — 배당은 사용자 돈이라 <b>항상 지급</b>(지급+입금 한 tx),
 * 재투자는 실패해도 배당금이 CMA 현금으로 남게 별도 tx. 만기예약 집행과 동형 패턴.
 */
@Service
@RequiredArgsConstructor
public class DividendPayoutService {

    private static final String CURRENCY_KRW = "KRW";
    private static final String STATUS_PAID = "PAID";
    private static final String SOURCE_DRIP = "DRIP";
    private static final String SIDE_BUY = "BUY";
    private static final String ORDER_TYPE_AMOUNT = "AMOUNT";
    /** 소수점 금액매수 최소(국내). 소액 배당은 CMA 잔돈으로 여기까지 채워 재매수. */
    private static final BigDecimal MIN_ORDER_KRW = BigDecimal.valueOf(1000);

    private final DividendPayoutMapper payoutMapper;
    private final CmaPoolPort cmaPoolPort;
    private final FractionalOrderService fractionalOrderService;

    /**
     * 보유자 1명 배당 지급 — 지급액 계산 → 지급 로그(PAID) → CMA 원화풀 입금. 한 트랜잭션.
     * 이미 지급(멱등 UNIQUE 충돌)이거나 지급액 0이면 {@code null} 반환(스케줄러가 스킵).
     *
     * @return 생성된 지급 로그(재투자 분기·마킹용), 스킵 시 null
     */
    @Transactional
    public DividendPayout payOut(String stockCode, BigDecimal perShare, LocalDate payDate, Holding holder) {
        // 지급액 = 보유수량 × 주당배당금, 원 단위 floor(세금 무시). 0이면 지급 없음.
        BigDecimal gross = holder.getQuantity().multiply(perShare).setScale(0, RoundingMode.FLOOR);
        if (gross.signum() <= 0) {
            return null;
        }
        DividendPayout payout = DividendPayout.builder()
                .userId(holder.getUserId())
                .stockCode(stockCode)
                .payDate(payDate)
                .holdingQty(holder.getQuantity())
                .perShare(perShare)
                .grossAmount(gross)
                .status(STATUS_PAID)
                .build();
        try {
            payoutMapper.insert(payout);
        } catch (DuplicateKeyException e) {
            return null;   // 같은 지급일 이미 지급됨 — 멱등 스킵.
        }
        // 배당금 CMA 원화풀 입금(항상). 멱등키 DIV:{payoutId}.
        cmaPoolPort.creditDividend(holder.getUserId(), CURRENCY_KRW, gross, payout.getId(),
                SOURCE_DRIP + ":" + payout.getId());
        return payout;
    }

    /**
     * 배당 재투자 — 받은 배당으로 같은 종목 소수점 매수 후 REINVESTED 마킹. 한 트랜잭션(매수+마킹).
     * 매수금액 = {@code max(배당금, 1,000원)} — 소액은 CMA 잔돈(+자동충전)으로 부족분 충당. 실패는 호출자가 잡아 FAILED 마킹.
     */
    @Transactional
    public void reinvest(DividendPayout payout) {
        BigDecimal reinvestAmount = payout.getGrossAmount().max(MIN_ORDER_KRW);
        FractionalOrderRequest req = new FractionalOrderRequest(
                SOURCE_DRIP + "_" + payout.getId(), payout.getStockCode(), SIDE_BUY,
                ORDER_TYPE_AMOUNT, reinvestAmount, null);
        SplitOrderResponse resp = fractionalOrderService.place(payout.getUserId(), req, SOURCE_DRIP);
        Long orderId = resp.wholeOrderId() != null ? resp.wholeOrderId() : resp.fractionalOrderId();
        payoutMapper.markReinvested(payout.getId(), orderId, reinvestAmount);
    }

    /** 재투자 실패 마킹 — 매수 트랜잭션이 롤백된 뒤 별도 tx로 사유 기록(배당금은 CMA 현금 잔류). */
    @Transactional
    public void markReinvestFailed(Long payoutId, String failReason) {
        payoutMapper.markReinvestFailed(payoutId, failReason);
    }

    /** 내 배당 지급/재투자 내역(최신순). */
    @Transactional(readOnly = true)
    public List<DividendPayoutResponse> history(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return payoutMapper.findByUserId(userId).stream()
                .map(DividendPayoutResponse::from)
                .toList();
    }
}
