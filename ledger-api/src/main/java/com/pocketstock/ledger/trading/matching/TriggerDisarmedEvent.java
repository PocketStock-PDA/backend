package com.pocketstock.ledger.trading.matching;

/**
 * 수익률 트리거 해제 이벤트(#194 실시간) — AFTER_COMMIT 발행. {@link AutoInvestTriggerEngine}이 받아
 * 인덱스에서 내리고 그 종목 트리거 0건이면 호가 구독 OFF. 트리거 해제(DELETE)가 발행.
 */
public record TriggerDisarmedEvent(Long triggerId, String stockCode) {
}
