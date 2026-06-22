package com.pocketstock.core.asset.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 잠자는 잔돈 스캔(GET /api/assets/scan) 응답 — 소스별 묶음 + 총액(모두 KRW).
 * 필드 형태는 CMA 홈 collectSources(sourceType/name/amount)와 정렬(동일 계산 단일화, F-E).
 * <ul>
 *   <li>ACCOUNT: 연동 계좌 끝전(balance % threshold)</li>
 *   <li>CARD:    카드 라운드업 잔돈</li>
 *   <li>POINT:   포인트 잔액</li>
 *   <li>FX:      외화 지갑 KRW 환산(매매기준율)</li>
 * </ul>
 */
public record ScanResponse(
        BigDecimal totalAmount,
        List<Source> sources
) {
    public record Source(
            String sourceType,   // ACCOUNT / CARD / POINT / FX
            String name,
            BigDecimal amount
    ) {}
}
