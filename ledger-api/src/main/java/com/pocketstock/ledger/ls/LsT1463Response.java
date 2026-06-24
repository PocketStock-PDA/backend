package com.pocketstock.ledger.ls;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * LS 거래대금상위(t1463) 응답 중 순위 표시에 쓰는 필드만 매핑.
 * 한 응답에 거래대금(value)·시가총액(total)을 함께 주므로 두 정렬을 한 소스로 처리한다.
 *
 * <p>단위 주의: value(거래대금)=백만원, total(시가총액)=억원. 서비스에서 원(KRW)으로 환산한다.
 * <p>연속조회: 요청 idx=0 → 응답 t1463OutBlock.idx 를 다음 요청 idx 로 올리고
 * 응답 헤더 tr_cont=Y 인 동안 이어 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LsT1463Response(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("t1463OutBlock") OutBlock outBlock,
        @JsonProperty("t1463OutBlock1") List<Item> outBlock1
) {
    /** 연속조회 커서. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutBlock(@JsonProperty("idx") int idx) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("shcode") String shcode,   // 종목코드(6자리) — tradable_stocks 조인키
            @JsonProperty("hname") String hname,      // 한글종목명
            @JsonProperty("price") BigDecimal price,  // 현재가
            @JsonProperty("sign") String sign,        // 전일대비구분
            @JsonProperty("change") BigDecimal change,// 전일대비
            @JsonProperty("diff") BigDecimal diff,    // 등락율(%)
            @JsonProperty("volume") BigDecimal volume,// 누적거래량
            @JsonProperty("value") BigDecimal value,  // 거래대금(백만원)
            @JsonProperty("total") BigDecimal total   // 시가총액(억원)
    ) {
    }
}
