package com.pocketstock.ledger.recon;

import java.util.List;

/**
 * 불변식 1개의 검산 결과 — 통화별/종목별 {@link ReconLine} 모음.
 *
 * @param name      검산 식별자(예: fx_conservation)
 * @param invariant 사람이 읽는 불변식 설명
 * @param balanced  모든 line이 0으로 닫히면 true
 * @param lines     단위별 상세
 */
public record ReconCheck(String name, String invariant, boolean balanced, List<ReconLine> lines) {
}
