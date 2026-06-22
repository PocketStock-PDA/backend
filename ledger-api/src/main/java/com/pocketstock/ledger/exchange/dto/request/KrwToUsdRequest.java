package com.pocketstock.ledger.exchange.dto.request;

import java.math.BigDecimal;

/**
 * 원화 → 달러 환전 체결({@code POST /api/exchange/krw-to-usd}) 요청.
 * {@code krwAmount}는 차감할 원화(KRW). 거래 인증은 본문 비밀번호가 아니라 사전 거래 세션(txn-auth)으로 처리한다.
 * {@code idempotencyKey}는 클라 발급 멱등키 — 따닥 탭·네트워크 재전송 시 같은 값을 보내면
 * 서버가 기존 환전 결과를 반환(중복 체결·이중 차감 방지, #96 item3 / H2와 동형).
 */
public record KrwToUsdRequest(
        BigDecimal krwAmount,
        String idempotencyKey
) {
}
