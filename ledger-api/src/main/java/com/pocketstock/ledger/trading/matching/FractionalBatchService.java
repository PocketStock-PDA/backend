package com.pocketstock.ledger.trading.matching;

import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.TradingRound;
import com.pocketstock.ledger.trading.mapper.OrderMapper;
import com.pocketstock.ledger.trading.service.FractionalGroupSettler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 소수점 배치 집행기(#153) — 차수의 QUEUED 주문을 종목·side·체결방식별 배치로 묶어 그룹마다 정산기로 넘긴다.
 * 그룹당 독립 트랜잭션({@link FractionalGroupSettler#settleGroup})이라 한 종목 실패가 다른 종목을 막지 않는다.
 * 실패 그룹은 거부+자금원복({@link FractionalGroupSettler#rejectGroup}, 별도 tx)으로 닫는다.
 *
 * <p>차수 선점(OPEN→EXECUTING)·완료표시(SETTLED/FAILED)는 스케줄러({@link FractionalRoundScheduler})가 맡고,
 * 여긴 한 차수의 집행 본문(그룹 분배·정산 위임)만 담당한다. 자신은 트랜잭션 경계를 두지 않는다(그룹별로 분리).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FractionalBatchService {

    private final OrderMapper orderMapper;
    private final FractionalGroupSettler settler;

    /** 한 차수 집행 — QUEUED를 그룹핑해 그룹별로 정산(독립 tx). 실패 그룹은 거부+환원. */
    public void executeRound(TradingRound round) {
        List<Order> queued = orderMapper.findQueuedByRound(round.getId());
        if (queued.isEmpty()) {
            return;   // 빈 차수(그 분에 접수 없음) — 정상, 바로 SETTLED
        }
        // 배치 그룹 키 = 종목 + side + 가격모델. AMOUNT 매수만 DOMESTIC_TICK(현재가+5틱), 나머지 MARKET.
        Map<String, List<Order>> groups = queued.stream()
                .collect(Collectors.groupingBy(FractionalBatchService::batchKey));
        log.info("[소수점배치] round={} market={} QUEUED {}건 → {}개 그룹 집행 시작",
                round.getId(), round.getMarket(), queued.size(), groups.size());

        for (List<Order> group : groups.values()) {
            Order head = group.get(0);
            String pricing = pricingMethod(head);
            try {
                settler.settleGroup(group, head.getStockCode(), head.getExchange(),
                        head.getSide(), pricing, round.getId());
            } catch (Exception e) {
                // 그룹 정산 tx는 롤백(돈 안 움직임) — 별도 tx로 거부+자금원복(#154).
                log.error("[소수점배치] 그룹 정산 실패 stock={} {} — 거부+환원: {}",
                        head.getStockCode(), head.getSide(), e.getMessage());
                try {
                    settler.rejectGroup(group, "배치 정산 실패: " + e.getMessage());
                } catch (Exception re) {
                    log.error("[소수점배치] 거부 환원도 실패 stock={} — 수동 점검 필요", head.getStockCode(), re);
                }
            }
        }
    }

    /** 배치 그룹 키: stockCode|side|pricingMethod. 같은 종목·방향·가격모델이면 한 블록으로 합산. */
    private static String batchKey(Order o) {
        return o.getStockCode() + "|" + o.getSide() + "|" + pricingMethod(o);
    }

    /** 국내 거래소(KRX) — DOMESTIC_TICK(현재가+5틱)은 KRX 호가단위 전용. 해외는 항상 MARKET. */
    private static final java.util.Set<String> DOMESTIC_EXCHANGES = java.util.Set.of("KOSPI", "KOSDAQ");

    /**
     * 가격모델: <b>국내</b> 금액매수만 DOMESTIC_TICK(현재가+5틱), 그 외(수량매수·매도·<b>모든 해외</b>)는
     * MARKET(실행시점 시장가). 해외 금액매수에 KRX +5틱을 오적용하지 않도록 거래소로 게이트(#155).
     */
    private static String pricingMethod(Order o) {
        boolean domestic = DOMESTIC_EXCHANGES.contains(o.getExchange());
        return domestic && "BUY".equals(o.getSide()) && "AMOUNT".equals(o.getOrderType()) ? "DOMESTIC_TICK" : "MARKET";
    }
}
