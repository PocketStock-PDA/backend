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

    /** 지급일 배당 일정(주당배당금) — 배당 지급 엔진이 보유자와 조인. */
    @GetMapping("/internal/calendar/dividend-payouts")
    List<DividendPayoutScheduleView> getDividendPayouts(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);
}
