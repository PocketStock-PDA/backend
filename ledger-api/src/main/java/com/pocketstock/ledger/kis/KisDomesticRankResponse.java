package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * KIS 국내주식 거래량순위(FHPST01710000) 응답 중 사용하는 필드만 매핑.
 * 거래대금 순위는 요청 시 {@code FID_BLNG_CLS_CODE=3}(거래금액순)으로 정렬해 받는다.
 * 거래대금 값은 누적 거래대금(acml_tr_pbmn). 최대 30건, 다음 조회 불가.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisDomesticRankResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") List<Item> output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("hts_kor_isnm") String name,        // 종목명
            @JsonProperty("mksc_shrn_iscd") String stockCode, // 단축 종목코드(6자리)
            @JsonProperty("data_rank") String rank,           // 순위
            @JsonProperty("stck_prpr") String price,          // 현재가
            @JsonProperty("acml_tr_pbmn") String tradeAmount  // 누적 거래대금
    ) {
    }
}
