package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.exchange.service.AutoFxService;
import com.pocketstock.ledger.trading.port.CmaAutoChargePort;
import com.pocketstock.ledger.trading.port.CmaPoolPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 주문 ↔ CMA풀 자동 자금연동 조율(#174) — 매수 충당·매도 환류를 <b>주문 트랜잭션 안에서</b> 수행한다.
 *
 * <p>예수금 leg은 trading이 {@link DepositService}로 직접, CMA풀 leg은 {@link CmaPoolPort}로 cma에 위임한다.
 * 두 leg이 호출자({@code WholeOrderService}/체결 정산)의 {@code @Transactional}에 합류해 체결과 한 단위로
 * 커밋·롤백된다 — 충당 후 체결이 실패하면 충당까지 통째 롤백돼 역이체가 필요 없다([[cma-settlement-immediate-flow]]).
 *
 * <p>거래 인증(비번)은 호출자가 주문 진입에서 {@code TxnAuthGuard}로 1회 처리한다(여기선 다루지 않음).
 * 멱등키는 주문 id 파생({@code order:{id}:buyfund:*} / {@code :sellret:*})으로 leg마다 결정적이라 재시도 안전.
 */
@Service
@RequiredArgsConstructor
public class OrderFundingService {

    private static final String TX_IN_TRANSFER = "IN_TRANSFER";    // 예수금 입금(+) — 매수 충당
    private static final String TX_OUT_TRANSFER = "OUT_TRANSFER";  // 예수금 출금(−) — 매도 환류
    private static final String REF_TYPE_ORDER = "ORDER";
    private static final String CURRENCY_USD = "USD";

    private final DepositService depositService;
    private final CmaPoolPort cmaPool;
    private final CmaAutoChargePort autoChargePort;
    private final AutoFxService autoFxService;

    /**
     * 매수 충당 — 예수금 주문가능이 필요액보다 모자라면 부족분만큼 CMA풀 → 예수금으로 끌어온다.
     * 충당이 필요 없으면(주문가능 ≥ 필요액) 아무 것도 하지 않는다. 호출자 트랜잭션에 합류.
     *
     * @param requiredAmount 이 매수가 차감할 체결 필요액(주문 통화)
     */
    public void transferForBuy(Long userId, Long accountId, String currency,
                               BigDecimal requiredAmount, Long orderId) {
        BigDecimal shortage = requiredAmount.subtract(depositService.getAvailable(accountId));
        if (shortage.signum() <= 0) {
            return;   // 예수금 주문가능으로 충분 — 충당 불필요
        }
        // 해외(USD): CMA 달러풀이 부족하면 설정대로 원화풀에서 자동환전해 달러풀을 먼저 채운다(#172).
        // 환전이 같은 트랜잭션이라, 이후 출금/체결이 실패하면 자동환전까지 통째 롤백된다.
        if (CURRENCY_USD.equals(currency)) {
            autoFxService.ensureUsdForOrder(userId, shortage, cmaPool.poolBalance(userId, currency), orderId);
        } else {
            // 국내(KRW): CMA 원화풀이 부족하면 부족금액 자동충전(은행 → CMA)으로 먼저 채운다(#193). OFF면 무동작.
            // 충전까지 같은 트랜잭션 — 이후 출금/체결 실패 시 자동충전도 통째 롤백된다.
            autoChargePort.ensureKrwPoolForBuy(userId, shortage, orderId);
        }
        String base = "order:" + orderId;
        // ① CMA풀 출금(BUY_TRANSFER) 먼저 — 풀 잔액 부족이면 예수금 입금 전에 막혀 롤백(부분반영 없음).
        cmaPool.withdrawForBuy(userId, currency, shortage, orderId, base + ":buyfund:cma");
        // ② 예수금 입금(IN_TRANSFER) — 같은 트랜잭션에 합류.
        depositService.record(userId, accountId, TX_IN_TRANSFER, shortage,
                currency, REF_TYPE_ORDER, orderId, base + ":buyfund:dep");
    }

    /**
     * 매도 환류 — 방금 예수금에 입금된 매도대금 전액을 즉시 CMA풀로 되돌린다(A안 즉시환류).
     * 예수금에 매도대금이 잔류하지 않는다. 호출자 트랜잭션에 합류.
     *
     * @param amount 환류할 매도대금(주문 통화) — 직전 체결 입금액과 동일
     */
    public void returnFromSell(Long userId, Long accountId, String currency,
                               BigDecimal amount, Long orderId) {
        String base = "order:" + orderId;
        // ① 예수금 출금(OUT_TRANSFER) 먼저 — 방금 입금돼 있으니 통과. ② CMA풀 입금(SELL_RETURN).
        depositService.record(userId, accountId, TX_OUT_TRANSFER, amount.negate(),
                currency, REF_TYPE_ORDER, orderId, base + ":sellret:dep");
        cmaPool.depositFromSell(userId, currency, amount, orderId, base + ":sellret:cma");
    }

    /**
     * 충당 반납(sweep) — 지정가 PENDING 매수의 충당분 중 안 쓴 것을 CMA풀로 되돌린다.
     * 예수금 <b>주문가능(balance−held)</b> 전액을 환류하므로, 취소(충당분 전액)·가격개선(지정가−체결가 잔류분)을
     * 한 경로로 처리하고 <b>다른 PENDING 주문의 hold는 held라 건드리지 않는다</b>(충당액 추적 불필요).
     * 주문가능이 0 이하면 반납할 게 없어 무시. 호출자 트랜잭션에 합류.
     *
     * @param reason 멱등키 구분자(예: {@code cancel} | {@code improve}) — 취소/가격개선이 같은 주문에서 안 겹치게
     */
    public void revertUnusedFunding(Long userId, Long accountId, String currency,
                                    Long orderId, String reason) {
        BigDecimal available = depositService.getAvailable(accountId);
        if (available.signum() <= 0) {
            return;   // 예수금에 남은 주문가능 없음 — 반납할 충당분 없음
        }
        String base = "order:" + orderId + ":" + reason;
        // ① 예수금 출금(OUT_TRANSFER) 먼저 → ② CMA풀 입금(REVERT, BUY_TRANSFER 보상).
        depositService.record(userId, accountId, TX_OUT_TRANSFER, available.negate(),
                currency, REF_TYPE_ORDER, orderId, base + ":dep");
        cmaPool.revertBuyTransfer(userId, currency, available, orderId, base + ":cma");
    }
}
