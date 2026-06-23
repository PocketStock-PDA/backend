package com.pocketstock.ledger.exchange.dto.response;

import com.pocketstock.ledger.exchange.FxDirection;

import java.math.BigDecimal;

/**
 * 환전 가능여부·가능금액 검증({@code GET /api/exchange/validate}) 응답 — 읽기 전용 dry-run.
 *
 * <p>체결 전 미리보기+게이트: {@code valid}로 버튼 활성화, {@code reason}으로 사유 표시,
 * {@code maxAmount}로 "전액" 버튼 프리필, {@code expectedReceive}로 정확한 수령액 미리보기
 * (체결과 같은 {@code FxQuoteCalculator}라 절사까지 일치).
 *
 * <p>{@code amount} 미지정 시 {@code inputAmount}/{@code expectedReceive}=null,
 * {@code valid}는 "환전 자체 가능 여부"(환율 가용 + 잔액>0)만 반영.
 */
public record ExchangeValidateResponse(
        boolean valid,                // 환전 가능 여부
        String direction,             // KRW_TO_USD | USD_TO_KRW
        String fromCurrency,          // 차감 통화 (KRW | USD)
        String toCurrency,            // 입금 통화
        BigDecimal appliedRate,       // 적용환율(매수 or 매도). 환율 불가 시 null
        BigDecimal inputAmount,       // 입력 금액 echo. 미지정 시 null
        BigDecimal expectedReceive,   // 절사 적용된 예상 수령액. 미지정/불가 시 null
        BigDecimal availableBalance,  // from풀 현재 잔액
        BigDecimal maxAmount,         // 가능금액 = 환전 가능 상한(잔액 전액)
        String reason                 // valid=false 사유코드. valid면 null
) {

    /** 사유코드 — 클라가 메시지 매핑. */
    public static final String RATE_UNAVAILABLE = "RATE_UNAVAILABLE";     // 환율 일시 불가(캐시·폴백 모두 빔)
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE"; // 잔액 부족
    public static final String BELOW_MINIMUM = "BELOW_MINIMUM";           // 수령액 1센트/1원 미만
    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";         // 금액 0 이하

    /** 환율 자체가 없어 환산 불가 — 잔액/금액 따질 것 없이 불가. */
    public static ExchangeValidateResponse rateUnavailable(FxDirection d, BigDecimal inputAmount) {
        return new ExchangeValidateResponse(false, d.name(), d.from(), d.to(),
                null, inputAmount, null, null, null, RATE_UNAVAILABLE);
    }
}
