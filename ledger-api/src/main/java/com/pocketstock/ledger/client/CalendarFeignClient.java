package com.pocketstock.ledger.client;

import com.pocketstock.ledger.client.dto.StockEventUpsertRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "core-api-calendar", url = "${feign.core-api.url}")
public interface CalendarFeignClient {

    @PostMapping("/internal/calendar/stock-events")
    void upsertStockEvents(@RequestBody List<StockEventUpsertRequest> events);
}
