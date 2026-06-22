package com.pocketstock.ledger.cma.dto.request;

import java.math.BigDecimal;

/**
 * core→ledger 내부 호출(쓰기)용 휴면계좌 해지 입금 요청 — 화면 노출 DTO 아님.
 *
 * <p>core(DB A)가 휴면 은행 계좌를 소프트 해지하며 그 잔액을 사용자 CMA 풀로 옮길 때 호출한다.
 * 인증은 {@code @CurrentUserId} 대신 {@code userId} 본문 — 잔돈 스캔의 internal read와 동일 패턴.
 * 거래유형({@code DORMANT})·참조({@code LINKED_BANK_ACCOUNT})·멱등키({@code DORMANT:{accountId}})는
 * core가 정하지 않고 ledger가 강제한다(원장 어휘는 ledger 소유).
 */
public record InternalCmaCreditRequest(
        Long userId,
        Long accountId,        // 해지된 linked_bank_accounts.id → ref_id, 멱등키 파생
        BigDecimal amount,     // 입금액(+), 해지 계좌 잔액
        String currency        // KRW | USD (해지 계좌 통화)
) {
}
