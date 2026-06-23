package com.pocketstock.ledger.trading.dto;

import java.math.BigDecimal;

/**
 * 온주 전환 결과. convertedWholeQty주가 소수점→온주로 굳고, remainingFractional만 소수로 남는다.
 * wholeQty=전환 후 직접소유 온주 합(=총 − 소수). totalQuantity=총 보유(불변).
 */
public record WholeShareConvertResponse(
        String stockCode,
        int convertedWholeQty,
        BigDecimal remainingFractional,
        BigDecimal wholeQty,
        BigDecimal totalQuantity
) {
}
