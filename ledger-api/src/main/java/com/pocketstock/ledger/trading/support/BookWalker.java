package com.pocketstock.ledger.trading.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 호가 사다리 훑기(walk the book) — 최우선부터 칸별 잔량을 주문수량만큼 누적해 가중평균가를 낸다.
 * 즉시체결(WholeOrderService)과 지정가 PENDING 매칭(WholeOrderMatchingEngine)이 공유하는 단일 체결 산정.
 * 부분체결 없음(ERD-04): 사다리 깊이로 전량 못 채우면 complete=false.
 */
public final class BookWalker {

    private BookWalker() {
    }

    /** 사다리 훑기 결과: 전량 채움(complete) 여부 + 가중평균가(미완이면 null). */
    public record Fill(boolean complete, BigDecimal avgPrice) {
    }

    /**
     * 최우선부터 칸별 잔량을 누적, 전량 채우면 가중평균가 반환. 빈 칸(가격/잔량 0)에서 중단(사다리 끝).
     * @param limitCap 지정가 상·하한(null=시장가) — 매수=초과 호가, 매도=미만 호가에서 중단.
     * @param buy 매수면 매도호가(ask) 사다리, 매도면 매수호가(bid) 사다리를 넘긴다.
     */
    public static Fill walk(BigDecimal[] prices, BigDecimal[] volumes, BigDecimal quantity,
                            BigDecimal limitCap, boolean buy) {
        BigDecimal remaining = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (int i = 0; i < prices.length && remaining.signum() > 0; i++) {
            if (prices[i].signum() <= 0 || volumes[i].signum() <= 0) {
                break;  // 가격/잔량 0 = 더 이상 깊이 없음
            }
            if (limitCap != null
                    && (buy ? prices[i].compareTo(limitCap) > 0 : prices[i].compareTo(limitCap) < 0)) {
                break;  // 지정가 범위 밖 — 더는 체결 불가
            }
            BigDecimal take = volumes[i].min(remaining);
            totalCost = totalCost.add(prices[i].multiply(take));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            return new Fill(false, null);
        }
        return new Fill(true, totalCost.divide(quantity, 4, RoundingMode.HALF_UP));
    }
}
