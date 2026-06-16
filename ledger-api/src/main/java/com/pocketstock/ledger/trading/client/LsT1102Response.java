package com.pocketstock.ledger.trading.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LS t1102(주식 현재가) 응답 중 사용하는 필드만 매핑.
 * 전체 OutBlock은 100+ 필드라 필요한 것만 받고 나머지는 무시한다.
 * ※ diff(등락율)는 LS가 문자열("1.41")로 내려줌 → String으로 받아 서비스에서 파싱.
 *   change(전일대비)는 부호 없는 절대값 → sign(4·5=하락)으로 부호 적용 필요.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LsT1102Response(
        @JsonProperty("t1102OutBlock") OutBlock outBlock,
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutBlock(
            @JsonProperty("hname") String hname,
            @JsonProperty("shcode") String shcode,
            @JsonProperty("price") long price,       // 현재가
            @JsonProperty("sign") String sign,       // 전일대비구분(1상한 2상승 3보합 4하한 5하락)
            @JsonProperty("change") long change,     // 전일대비(절대값)
            @JsonProperty("diff") String diff,       // 등락율(문자열)
            @JsonProperty("volume") long volume,     // 누적거래량
            @JsonProperty("open") long open,         // 시가
            @JsonProperty("high") long high,         // 고가
            @JsonProperty("low") long low            // 저가
    ) {
    }
}
