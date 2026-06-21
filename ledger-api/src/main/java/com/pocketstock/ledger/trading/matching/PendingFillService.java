package com.pocketstock.ledger.trading.matching;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateCache;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import com.pocketstock.ledger.trading.domain.OrderStatus;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.service.DepositService;
import com.pocketstock.ledger.trading.service.OperatingCashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 온주 지정가 PENDING 1건의 체결 확정(매칭 데몬 전용, 국내·해외). 진입 시 묶어둔 hold(M2)를
 * 실차감/실인도로 전환하고 상태를 PENDING→FILLED로 넘긴다. 한 로컬 트랜잭션 — 돈·수량·상태가 함께 커밋.
 * 예수금 차감은 주문 통화 그대로(국내 KRW·해외 USD), 원화 취득원가만 해외는 체결시점 환율로 환산한다.
 *
 * <p>취소와의 경합은 {@code transitionFill}(PENDING→FILLED 조건부 UPDATE)이 선점으로 차단한다.
 * 0행이면 그 사이 취소·체결된 것 → 정산에 진입하지 않고 {@code false}(돈 안 움직임). orders 행 잠금이
 * {@code cancelIfCancellable}과 직렬화되어 {취소, 체결} 중 정확히 하나만 성공한다.
 */
@Service
@RequiredArgsConstructor
public class PendingFillService {

    private static final String CURRENCY_USD = "USD";

    private final OrderMapper orderMapper;
    private final DepositService depositService;
    private final OperatingCashService operatingCashService;
    private final HoldingMapper holdingMapper;
    private final CurrencyRateCache currencyRateCache;

    /**
     * @return true=이 호출이 체결을 확정, false=경합에서 짐(이미 취소·체결됨).
     */
    @Transactional
    public boolean fill(PendingFill cmd) {
        // 선점: PENDING일 때만 FILLED로. 0행이면 취소·중복에 짐 → 정산 진입 안 함(돈 안 건드림).
        if (orderMapper.transitionFill(cmd.orderId(), OrderStatus.PENDING, OrderStatus.FILLED, cmd.fillPrice()) == 0) {
            return false;
        }
        String idemKey = "order:" + cmd.orderId();   // 즉시체결과 동일한 결정적 멱등키
        BigDecimal total = cmd.fillPrice().multiply(cmd.quantity());       // 실제 체결대금
        BigDecimal heldNotional = cmd.limitPrice().multiply(cmd.quantity()); // 진입 시 묶은 명목(지정가×수량)
        if ("BUY".equals(cmd.side())) {
            // 묶은 명목 해제 → 실제 체결대금만큼 예수금 실차감(가격개선분은 주문가능으로 환원).
            depositService.releaseHold(cmd.accountId(), heldNotional);
            depositService.record(cmd.userId(), cmd.accountId(), "BUY",
                    total.negate(), cmd.currency(), "order", cmd.orderId(), idemKey);
            // 복식부기 상대 leg(H1): 회사 현금 수취(+).
            operatingCashService.record("BUY", total, cmd.currency(), "order", cmd.orderId(), idemKey);
            // 원화 취득원가 = 국내는 체결대금 그대로, 해외는 체결 시점 실시간 환율로 환산(즉시체결 경로와 동일).
            BigDecimal krwAmount = CURRENCY_USD.equals(cmd.currency()) ? total.multiply(fxRateForKrwBasis()) : total;
            holdingMapper.upsertBuy(cmd.userId(), cmd.accountId(), cmd.stockCode(),
                    cmd.quantity(), cmd.fillPrice(), krwAmount, cmd.currency());
        } else {
            // 묶은 수량 해제 → 보유 수량 실차감(release 후 매도가능이 복원돼 reduceForSell 가드 통과).
            holdingMapper.releaseSellReserve(cmd.accountId(), cmd.stockCode(), cmd.quantity());
            if (holdingMapper.reduceForSell(cmd.accountId(), cmd.stockCode(), cmd.quantity()) == 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "매도 체결 수량 차감 실패(보유 부족)");
            }
            depositService.record(cmd.userId(), cmd.accountId(), "SELL",
                    total, cmd.currency(), "order", cmd.orderId(), idemKey);
            // 복식부기 상대 leg(H1): 회사 현금 지급(−).
            operatingCashService.record("SELL", total.negate(), cmd.currency(), "order", cmd.orderId(), idemKey);
        }
        return true;
    }

    /**
     * 해외 매수 원화원가 환산용 — 체결 시점 실시간 매매기준율(USD/KRW). 지정가 PENDING은 주문 시점이
     * 아니라 '실제 체결 시점'의 환율을 취득원가로 박아야 맞다(즉시체결 {@code WholeOrderService}와 동일 소스).
     * 콜드스타트(환율 틱 미수신)면 던져서 이번 체결을 보류 → 매칭 엔진이 다음 틱에 재시도.
     */
    private BigDecimal fxRateForKrwBasis() {
        CurrencyRateResponse rate = currencyRateCache.get();
        if (rate == null || rate.exchangeRate() == null || rate.exchangeRate().signum() <= 0) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환율 정보를 아직 받지 못했습니다.");
        }
        return rate.exchangeRate();
    }

    /** 체결 1건 명령 — 인덱스 스냅샷(side·limitPrice·quantity 등)에 매칭이 산정한 체결가(fillPrice)를 더한 것. */
    public record PendingFill(Long orderId, Long userId, Long accountId, String stockCode,
                              String side, BigDecimal limitPrice, BigDecimal quantity,
                              String currency, BigDecimal fillPrice) {
    }
}
