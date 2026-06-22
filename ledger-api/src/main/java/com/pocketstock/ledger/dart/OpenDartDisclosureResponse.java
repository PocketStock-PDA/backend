package com.pocketstock.ledger.dart;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenDartDisclosureResponse(
        String status,
        String message,
        List<Item> list
) {
    public record Item(
            @JsonProperty("corp_name")   String corpName,
            @JsonProperty("stock_code")  String stockCode,
            @JsonProperty("report_nm")   String reportNm,
            @JsonProperty("rcept_dt")    String rceptDt   // yyyyMMdd
    ) {}
}
