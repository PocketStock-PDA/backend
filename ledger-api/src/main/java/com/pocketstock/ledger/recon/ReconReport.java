package com.pocketstock.ledger.recon;

import java.util.List;

/**
 * 원장 정합성 검산(recon) 전체 보고서 — 모든 복식부기 불변식 검사의 묶음(#96 item4).
 *
 * <p>읽기 전용 대사: 새 테이블 없이 기존 원장을 합산해 차변=대변이 유지되는지 확인한다.
 * {@code balanced=false}면 어떤 불변식이 깨졌는지 {@link ReconCheck}에서 통화/종목 단위로 드러난다
 * (돈·주식이 무에서 생겼거나 샜다는 신호).
 *
 * @param balanced 모든 검사가 통과하면 true
 * @param checks   불변식별 검사 결과
 */
public record ReconReport(boolean balanced, List<ReconCheck> checks) {
}
