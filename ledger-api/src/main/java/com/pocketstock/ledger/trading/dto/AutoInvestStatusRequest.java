package com.pocketstock.ledger.trading.dto;

/**
 * 자동모으기 종목 상태 변경 — 일시중지/재개.
 * action=PAUSE(중지, is_active=false) / RESUME(재개, is_active=true). 해제는 DELETE /auto-invest/{id}.
 */
public record AutoInvestStatusRequest(
        String action   // PAUSE | RESUME
) {
}
