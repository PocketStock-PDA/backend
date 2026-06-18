package com.pocketstock.ledger.trading.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * 브로커 시세 필드(LS JSON·KIS 캐럿) 공통 파싱 유틸.
 * 외부 시세는 문자·공백·빈 값이 섞여 오므로 BigDecimal·long으로 안전 변환한다(빈 값/공백은 0).
 * 서비스(REST)·실시간 리스너가 같은 규칙을 쓰도록 단일 지점으로 모았다.
 */
public final class MarketFields {

    private MarketFields() {
    }

    /** 문자열 숫자 필드 → BigDecimal. 빈 값/공백은 0. */
    public static BigDecimal dec(String value) {
        String t = (value == null) ? "" : value.trim();
        return t.isEmpty() ? BigDecimal.ZERO : new BigDecimal(t);
    }

    /** 문자열 수량 필드 → long. 빈 값/공백은 0. */
    public static long lng(String value) {
        String t = (value == null) ? "" : value.trim();
        return t.isEmpty() ? 0L : new BigDecimal(t).longValue();
    }

    /** JSON 노드의 숫자 필드 → BigDecimal. 빈 값/공백은 0. */
    public static BigDecimal dec(JsonNode node, String field) {
        return dec(node.path(field).asText(""));
    }

    /** JSON 노드의 수량 필드 → long. 빈 값/공백은 0. */
    public static long lng(JsonNode node, String field) {
        return lng(node.path(field).asText(""));
    }
}
