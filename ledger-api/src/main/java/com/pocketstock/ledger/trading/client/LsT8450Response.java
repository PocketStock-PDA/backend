package com.pocketstock.ledger.trading.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LS t8450((통합)주식현재가호가조회2) 응답 중 사용하는 필드만 매핑.
 * exchgubun=U(통합)로 호출 — 가격 사다리(offerho/bidho)는 통합 기준,
 * 잔량·총합은 통합(unx_*)을 사용해 실시간(UH1 통합호가잔량)과 사다리를 일치시킨다.
 * KRX 단독(offerrem)·NXT(nxt_*)·중간가(krx_mid*) 등은 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LsT8450Response(
        @JsonProperty("t8450OutBlock") OutBlock outBlock,
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutBlock(
            @JsonProperty("hname") String hname,
            @JsonProperty("shcode") String shcode,
            @JsonProperty("price") long price,            // 현재가
            @JsonProperty("uplmtprice") long upperLimit,   // 상한가
            @JsonProperty("dnlmtprice") long lowerLimit,   // 하한가
            @JsonProperty("unx_offer") long offerTotal,    // 통합 매도호가수량합
            @JsonProperty("unx_bid") long bidTotal,        // 통합 매수호가수량합

            // 매도호가 1~10 (offerho1 = 최우선=최저). exchgubun=U → 통합 호가 사다리.
            @JsonProperty("offerho1") long offerho1, @JsonProperty("offerho2") long offerho2,
            @JsonProperty("offerho3") long offerho3, @JsonProperty("offerho4") long offerho4,
            @JsonProperty("offerho5") long offerho5, @JsonProperty("offerho6") long offerho6,
            @JsonProperty("offerho7") long offerho7, @JsonProperty("offerho8") long offerho8,
            @JsonProperty("offerho9") long offerho9, @JsonProperty("offerho10") long offerho10,
            // 통합 매도잔량 1~10 (unx_offerrem)
            @JsonProperty("unx_offerrem1") long offerrem1, @JsonProperty("unx_offerrem2") long offerrem2,
            @JsonProperty("unx_offerrem3") long offerrem3, @JsonProperty("unx_offerrem4") long offerrem4,
            @JsonProperty("unx_offerrem5") long offerrem5, @JsonProperty("unx_offerrem6") long offerrem6,
            @JsonProperty("unx_offerrem7") long offerrem7, @JsonProperty("unx_offerrem8") long offerrem8,
            @JsonProperty("unx_offerrem9") long offerrem9, @JsonProperty("unx_offerrem10") long offerrem10,

            // 매수호가 1~10 (bidho1 = 최우선=최고)
            @JsonProperty("bidho1") long bidho1, @JsonProperty("bidho2") long bidho2,
            @JsonProperty("bidho3") long bidho3, @JsonProperty("bidho4") long bidho4,
            @JsonProperty("bidho5") long bidho5, @JsonProperty("bidho6") long bidho6,
            @JsonProperty("bidho7") long bidho7, @JsonProperty("bidho8") long bidho8,
            @JsonProperty("bidho9") long bidho9, @JsonProperty("bidho10") long bidho10,
            // 통합 매수잔량 1~10 (unx_bidrem)
            @JsonProperty("unx_bidrem1") long bidrem1, @JsonProperty("unx_bidrem2") long bidrem2,
            @JsonProperty("unx_bidrem3") long bidrem3, @JsonProperty("unx_bidrem4") long bidrem4,
            @JsonProperty("unx_bidrem5") long bidrem5, @JsonProperty("unx_bidrem6") long bidrem6,
            @JsonProperty("unx_bidrem7") long bidrem7, @JsonProperty("unx_bidrem8") long bidrem8,
            @JsonProperty("unx_bidrem9") long bidrem9, @JsonProperty("unx_bidrem10") long bidrem10
    ) {
        /** 매도호가 1~10 (index 0 = 최우선). */
        public long[] askPrices() {
            return new long[]{offerho1, offerho2, offerho3, offerho4, offerho5,
                    offerho6, offerho7, offerho8, offerho9, offerho10};
        }

        /** 통합 매도잔량 1~10 (index 0 = 최우선). */
        public long[] askVolumes() {
            return new long[]{offerrem1, offerrem2, offerrem3, offerrem4, offerrem5,
                    offerrem6, offerrem7, offerrem8, offerrem9, offerrem10};
        }

        /** 매수호가 1~10 (index 0 = 최우선). */
        public long[] bidPrices() {
            return new long[]{bidho1, bidho2, bidho3, bidho4, bidho5,
                    bidho6, bidho7, bidho8, bidho9, bidho10};
        }

        /** 통합 매수잔량 1~10 (index 0 = 최우선). */
        public long[] bidVolumes() {
            return new long[]{bidrem1, bidrem2, bidrem3, bidrem4, bidrem5,
                    bidrem6, bidrem7, bidrem8, bidrem9, bidrem10};
        }
    }
}
