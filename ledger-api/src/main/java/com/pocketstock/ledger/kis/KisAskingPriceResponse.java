package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KIS 해외주식 현재가 호가(HHDFS76200100) 응답 중 사용하는 필드만 매핑.
 * output2는 배열이 아니라 pbid1~10/pask1~10/vbid1~10/vask1~10 평면 객체다.
 * 미국은 10호가, 그 외 국가는 1호가만 채워진다(나머지 0).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisAskingPriceResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output1") Output1 output1,
        @JsonProperty("output2") Output2 output2
) {
    /** 요약: 종목코드·실시간코드·호가시간·총잔량. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output1(
            @JsonProperty("rsym") String rsym,   // 실시간종목코드(DNASAAPL)
            @JsonProperty("code") String code,   // 종목코드(AAPL)
            @JsonProperty("dhms") String dhms,   // 호가시간
            @JsonProperty("bvol") String bvol,   // 매수호가총잔량
            @JsonProperty("avol") String avol    // 매도호가총잔량
    ) {
    }

    /** 호가 10단계(평면). pask1=최우선 매도(최저), pbid1=최우선 매수(최고). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output2(
            @JsonProperty("pask1") String pask1, @JsonProperty("pask2") String pask2,
            @JsonProperty("pask3") String pask3, @JsonProperty("pask4") String pask4,
            @JsonProperty("pask5") String pask5, @JsonProperty("pask6") String pask6,
            @JsonProperty("pask7") String pask7, @JsonProperty("pask8") String pask8,
            @JsonProperty("pask9") String pask9, @JsonProperty("pask10") String pask10,
            @JsonProperty("vask1") String vask1, @JsonProperty("vask2") String vask2,
            @JsonProperty("vask3") String vask3, @JsonProperty("vask4") String vask4,
            @JsonProperty("vask5") String vask5, @JsonProperty("vask6") String vask6,
            @JsonProperty("vask7") String vask7, @JsonProperty("vask8") String vask8,
            @JsonProperty("vask9") String vask9, @JsonProperty("vask10") String vask10,
            @JsonProperty("pbid1") String pbid1, @JsonProperty("pbid2") String pbid2,
            @JsonProperty("pbid3") String pbid3, @JsonProperty("pbid4") String pbid4,
            @JsonProperty("pbid5") String pbid5, @JsonProperty("pbid6") String pbid6,
            @JsonProperty("pbid7") String pbid7, @JsonProperty("pbid8") String pbid8,
            @JsonProperty("pbid9") String pbid9, @JsonProperty("pbid10") String pbid10,
            @JsonProperty("vbid1") String vbid1, @JsonProperty("vbid2") String vbid2,
            @JsonProperty("vbid3") String vbid3, @JsonProperty("vbid4") String vbid4,
            @JsonProperty("vbid5") String vbid5, @JsonProperty("vbid6") String vbid6,
            @JsonProperty("vbid7") String vbid7, @JsonProperty("vbid8") String vbid8,
            @JsonProperty("vbid9") String vbid9, @JsonProperty("vbid10") String vbid10
    ) {
        /** 매도호가 1~10 (index 0 = 최우선=최저). */
        public String[] askPrices() {
            return new String[]{pask1, pask2, pask3, pask4, pask5, pask6, pask7, pask8, pask9, pask10};
        }

        public String[] askVolumes() {
            return new String[]{vask1, vask2, vask3, vask4, vask5, vask6, vask7, vask8, vask9, vask10};
        }

        /** 매수호가 1~10 (index 0 = 최우선=최고). */
        public String[] bidPrices() {
            return new String[]{pbid1, pbid2, pbid3, pbid4, pbid5, pbid6, pbid7, pbid8, pbid9, pbid10};
        }

        public String[] bidVolumes() {
            return new String[]{vbid1, vbid2, vbid3, vbid4, vbid5, vbid6, vbid7, vbid8, vbid9, vbid10};
        }
    }
}
