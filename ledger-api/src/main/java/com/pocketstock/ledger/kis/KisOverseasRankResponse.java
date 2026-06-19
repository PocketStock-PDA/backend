package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * KIS 해외주식 거래대금순위(HHDFS76320010, 해외주식-044) 응답 중 사용하는 필드만 매핑.
 * 종목 목록은 output2 배열, 거래대금 값은 tamt. 모의투자 미지원(실전 토큰 필요).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisOverseasRankResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output2") List<Item> output2
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("excd") String excd,   // 거래소코드(NAS/NYS/AMS …)
            @JsonProperty("symb") String symb,   // 종목코드(AAPL)
            @JsonProperty("name") String name,   // 종목명
            @JsonProperty("ename") String ename, // 영문 종목명
            @JsonProperty("last") String price,  // 현재가
            @JsonProperty("rate") String rate,   // 등락율
            @JsonProperty("tamt") String tradeAmount, // 거래대금
            @JsonProperty("rank") String rank    // 순위
    ) {
    }
}
