package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;

/**
 * SOL트래블 외화잔액 연동 응답. {@code krwEquivalent}는 USD×매매기준율(정수)이며,
 * 환율 피드 미수신(콜드스타트) 시 {@code null}일 수 있다(외화지갑 적재 자체는 성공).
 */
public record FxLinkResponse(BigDecimal usdBalance, BigDecimal krwEquivalent) {
}
