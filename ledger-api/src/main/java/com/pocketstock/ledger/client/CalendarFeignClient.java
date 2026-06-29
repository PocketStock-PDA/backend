package com.pocketstock.ledger.client;

import com.pocketstock.ledger.client.dto.DividendPayoutScheduleView;
import com.pocketstock.ledger.client.dto.StockEventUpsertRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "core-api-calendar", url = "${feign.core-api.url}")
public interface CalendarFeignClient {

    @PostMapping("/internal/calendar/stock-events")
    void upsertStockEvents(@RequestBody List<StockEventUpsertRequest> events);

    /** 지급일 배당 일정(주당배당금) — 배당 지급 엔진이 보유자와 조인. 국내(KRW). */
    @GetMapping("/internal/calendar/dividend-payouts")
    List<DividendPayoutScheduleView> getDividendPayouts(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);

    /** 배당락일(EX) 기준 배당 일정 — 해외(US, 정확한 지급일 없어 EX에 주당배당금 적재). */
    @GetMapping("/internal/calendar/dividend-ex-payouts")
    List<DividendPayoutScheduleView> getDividendExPayouts(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);
}
