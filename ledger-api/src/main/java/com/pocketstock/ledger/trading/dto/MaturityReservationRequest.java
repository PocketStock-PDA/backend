package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 만기 후 배당주 매수 예약 생성 요청.
 *
 * <p>만기일·시장·통화는 받지 않는다 — 서버가 {@code linkedBankAccountId}로 연동은행계좌(DB A)를 조회해
 * 만기일을 스냅샷으로 고정하고, {@code stockCode}→거래소에서 시장·통화를 파생한다(클라 신뢰 X, 자동모으기와 동일).
 * 중복 예약(같은 계좌·종목)은 UNIQUE 제약이 막으므로 별도 멱등키는 두지 않는다.
 *
 * @param linkedBankAccountId 만기 트리거 겸 매수 자금 출처가 될 예적금 연동계좌 ID(본인 소유)
 * @param stockCode           매수할 배당주 종목코드(국내, KRW)
 * @param buyAmount           이 종목 매수금액(KRW, 슬라이더의 배당주 몫). 국내 최소주문 ≥ 1,000원
 */
public record MaturityReservationRequest(
        Long linkedBankAccountId,
        String stockCode,
        BigDecimal buyAmount
) {
}
