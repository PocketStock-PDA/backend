package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KisDividendResponse(
        @JsonProperty("rt_cd")  String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1")   String msg1,
        @JsonProperty("output1") List<Item> output1
) {
    public record Item(
            @JsonProperty("record_date")    String recordDate,
            @JsonProperty("sht_cd")         String shtCd,
            @JsonProperty("isin_name")      String isinName,
            @JsonProperty("divi_kind")      String diviKind,
            @JsonProperty("per_sto_divi_amt") String perStoDiviAmt,
            @JsonProperty("divi_rate")      String diviRate,
            @JsonProperty("divi_pay_dt")    String diviPayDt
    ) {}
}
