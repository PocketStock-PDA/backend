package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KIS 해외주식 현재가상세(HHDFS76200200) 응답 중 사용하는 필드만 매핑.
 * 시/고/저/현재가/전일종가/거래량으로 국내 StockPriceResponse·WS 체결가와 정렬한다.
 * 대비/등락율은 응답에 없어 last-base로 계산한다(서비스).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisPriceDetailResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") Output output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("last") String last,   // 현재가
            @JsonProperty("base") String base,   // 전일종가
            @JsonProperty("open") String open,   // 시가
            @JsonProperty("high") String high,   // 고가
            @JsonProperty("low") String low,     // 저가
            @JsonProperty("tvol") String tvol    // 거래량(누적)
    ) {
    }
}
