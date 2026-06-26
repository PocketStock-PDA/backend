package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포트폴리오 요약 — 전체/국내/해외 집계 + 종목별 평가·수익률. 화면 상단 총합과 보유 카드의 단일 소스.
 *
 * <p>평가(eval)는 현재가 스냅샷, 원금(invested)은 취득원가(국내=실원화 krw_cost_basis, 해외=수량×평균매입가 USD).
 * 환산 KRW는 {@code usdKrwRate}(현재 환율)로 계산하고, 해외 USD 수익률은 환차손익을 제외한다(전체 KRW 수익률은 포함).
 * 현재가·환율 조회 실패 종목은 집계에서 제외하고 종목 항목엔 {@code priced=false}로 남긴다(0원 오인 방지).
 */
public record PortfolioSummaryResponse(
        String asOf,                  // 스냅샷 시각(ISO-8601)
        BigDecimal usdKrwRate,        // 환산에 쓴 현재 USD/KRW 환율(해외 $↔₩ 토글용). 해외 없음/콜드스타트면 null
        Segment total,                // 전체(환산 KRW)
        Segment domestic,             // 국내(KRW)
        OverseasSegment overseas,     // 해외(USD + 참고 환산 KRW). 해외 보유 없으면 null
        List<HoldingValuation> holdings
) {

    /** 통화 단일(KRW) 집계 — 전체/국내 공용. */
    public record Segment(
            BigDecimal evalKrw,
            BigDecimal investedKrw,
            BigDecimal profitKrw,
            BigDecimal profitRate     // % (원금 0이면 0)
    ) {
    }

    /** 해외 집계 — USD native(환차 제외 수익률) + 현재 환율 환산 KRW(참고). */
    public record OverseasSegment(
            BigDecimal evalUsd,
            BigDecimal investedUsd,
            BigDecimal profitUsd,
            BigDecimal profitRate,    // % USD 기준(환차 제외)
            BigDecimal evalKrw,
            BigDecimal investedKrw,   // 매수시점 환율 기준(krw_cost_basis)
            BigDecimal profitKrw      // 환차손익 포함
    ) {
    }

    /** 종목별 평가 — native 통화 기준 + 환산 KRW. 카드·live 재계산 재료. */
    public record HoldingValuation(
            String stockCode,
            String currency,          // KRW / USD
            BigDecimal quantity,
            BigDecimal wholeQty,
            BigDecimal fractionalQty,
            BigDecimal avgBuyPrice,   // native
            BigDecimal currentPrice,  // native(현재가 스냅샷). priced=false면 null
            BigDecimal evalAmount,    // native = quantity × currentPrice. priced=false면 null
            BigDecimal invested,      // native(국내=krw_cost_basis, 해외=quantity×avgBuyPrice)
            BigDecimal profit,        // native = evalAmount − invested. priced=false면 null
            BigDecimal profitRate,    // % native. priced=false면 null
            BigDecimal evalKrw,       // 환산 KRW(국내=evalAmount). priced=false 또는 환율없음이면 null
            BigDecimal investedKrw,   // 환산 KRW 원금(해외=매수시점 환율 krw_cost_basis). 카드 원화표시용
            BigDecimal profitKrw,     // 환산 KRW 손익 = evalKrw − investedKrw. priced=false 또는 환율없음이면 null
            boolean priced            // 현재가 조회 성공 여부
    ) {
    }
}
