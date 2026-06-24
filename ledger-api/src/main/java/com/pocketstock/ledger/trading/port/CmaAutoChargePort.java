package com.pocketstock.ledger.trading.port;

import java.math.BigDecimal;

/**
 * 부족금액 자동충전(은행 → CMA 원화풀) — 매수 충당 시 CMA 원화풀이 모자라면 사용자 설정대로 은행에서 채운다(#193).
 *
 * <p>trading 매수 오케스트레이션({@code OrderFundingService})이 "이 매수가 CMA 원화풀에서 끌어갈 금액"을 넘기면,
 * cma 도메인이 자기 설정(ON/OFF·대상 은행계좌·1회 한도)을 보고 부족분만큼 은행 → CMA로 충전한다.
 * 설정·판단은 cma가 소유(캡슐화)하고, trading은 "부족 없게 채워줘"만 요청한다 — {@link CmaPoolPort}와 동형(trading→cma).
 *
 * <p>자동충전 OFF·대상계좌 미설정·CMA 없음이면 <b>무동작</b>(매수가 알아서 부족으로 실패). 한도로 캡돼 여전히
 * 부족하면 이후 CMA → 예수금 출금이 막혀 매수가 실패한다(의도된 동작 — 회차 로그에 FAILED). 호출자 트랜잭션에 합류.
 */
public interface CmaAutoChargePort {

    /**
     * 매수에 필요한 KRW만큼 CMA 원화풀을 채운다(자동충전 ON일 때만).
     *
     * @param requiredKrw 이 매수가 CMA 원화풀에서 끌어갈 금액(KRW)
     * @param orderId     멱등키 파생용 주문 id(중복 충전 차단)
     */
    void ensureKrwPoolForBuy(Long userId, BigDecimal requiredKrw, Long orderId);
}
