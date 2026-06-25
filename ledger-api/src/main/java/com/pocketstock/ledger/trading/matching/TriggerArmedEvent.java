package com.pocketstock.ledger.trading.matching;

/**
 * 수익률 트리거 등록/활성 이벤트(#194 실시간) — AFTER_COMMIT 발행. {@link AutoInvestTriggerEngine}이 받아
 * 인덱스에 올리고 그 종목 첫 트리거면 호가 구독 ON. 트리거 CRUD(등록/수정)가 발행.
 */
public record TriggerArmedEvent(Long triggerId, String stockCode) {
}
