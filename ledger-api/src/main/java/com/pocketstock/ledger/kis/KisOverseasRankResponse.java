package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * KIS 해외주식 순위 응답(output2) 매핑 — 거래대금순위(HHDFS76320010)·시가총액순위(HHDFS76350100) 공용.
 * 두 TR이 output2 배열을 공유하되 거래대금은 tamt, 시가총액은 tomv로 갈리므로 한 DTO에 둘 다 둔다
 * (해당 TR이 안 주는 값은 null). 모의투자 미지원(실전 토큰 필요).
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
            @JsonProperty("tamt") String tradeAmount, // 거래대금(거래대금순위 TR)
            @JsonProperty("tomv") String marketCap,   // 시가총액(시가총액순위 TR)
            @JsonProperty("rank") String rank    // 순위
    ) {
    }
}
