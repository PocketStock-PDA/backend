package com.pocketstock.ledger.exchange.dto.response;

import com.pocketstock.ledger.exchange.domain.FxTransaction;

import java.math.BigDecimal;
import java.util.List;

/**
 * 환전 이력 조회({@code GET /api/exchange/history}) 응답.
 * KRW/USD 금액은 방향과 무관하게 통화 기준으로 정규화해 프론트가 일관 렌더.
 */
public record FxHistoryResponse(
        List<Item> history,
        int page,
        long totalElements
) {

    public record Item(
            String type,            // KRW_TO_USD / USD_TO_KRW
            BigDecimal krwAmount,   // 원화 측 금액
            BigDecimal usdAmount,   // 달러 측 금액
            String triggerType,     // MANUAL / AUTO / RESIDUAL
            BigDecimal rate,        // 적용환율
            String status,          // DONE / FAILED
            String exchangedAt
    ) {
        public static Item from(FxTransaction tx) {
            boolean krwToUsd = "KRW".equals(tx.getFromCurrency());
            BigDecimal krw = krwToUsd ? tx.getFromAmount() : tx.getToAmount();
            BigDecimal usd = krwToUsd ? tx.getToAmount() : tx.getFromAmount();
            return new Item(
                    tx.getFromCurrency() + "_TO_" + tx.getToCurrency(),
                    krw, usd,
                    tx.getTriggerType(),
                    tx.getExchangeRate(),
                    tx.getStatus(),
                    tx.getCreatedAt() == null ? null : tx.getCreatedAt().toString());
        }
    }
}
