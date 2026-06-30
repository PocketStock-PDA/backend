package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래내역 항목.
 */
public record OrderHistoryResponse(
        Long orderId,
        String stockCode,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal orderAmount,
        BigDecimal price,
        String status,
        String currency,
        LocalDateTime createdAt,
        /** 체결금액 — 소수점은 allocations.gross_amount 합, 온주는 null(프론트가 체결가×수량). 미체결은 null. */
        BigDecimal filledAmount,
        /** 매도 체결 시점 평단(실현손익 산출 입력값). 매도 FILLED만 non-null. */
        BigDecimal avgBuyPriceAtSell,
        /** 해외 매도 체결 시점 USD/KRW 환율. 국내·매수·환율 취득 실패 시 null. */
        BigDecimal fxRateAtFill,
        /**
         * 실현손익(판매수익) — native 통화 = 체결금액 − 평단×수량. 매도 FILLED만 non-null.
         * 백엔드가 체결 스냅샷(평단·체결금액·체결환율)에서 단일 정책으로 산출(HALF_UP, USD 2·KRW 0자리).
         * 프론트는 이 값을 그대로 표시한다(라이브 재계산 금지 — 반올림 발산 방지).
         */
        BigDecimal realizedPnl,
        /**
         * 실현손익 환산 KRW. 해외 = realizedPnl(raw) × 체결환율(fx 없으면 null), 국내 = realizedPnl.
         * 월 판매수익 합계·환산 표시용. priced 무관(체결 스냅샷 기반).
         */
        BigDecimal realizedPnlKrw,
        /** REJECTED 사유. 정상 주문은 null. */
        String failReason
) {
}
