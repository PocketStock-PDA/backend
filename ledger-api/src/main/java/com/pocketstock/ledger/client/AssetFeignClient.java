package com.pocketstock.ledger.client;

import com.pocketstock.ledger.client.dto.CardRoundupSummary;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.PointSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "core-api", url = "${feign.core-api.url}")
public interface AssetFeignClient {

    @GetMapping("/internal/assets/accounts")
    List<LinkedAccountSummary> getLinkedAccounts(@RequestParam Long userId,
                                                  @RequestParam List<Long> enabledIds);

    @GetMapping("/internal/assets/card-roundup")
    CardRoundupSummary getCardRoundup(@RequestParam Long userId,
                                       @RequestParam Long linkedAccountId);

    @PatchMapping("/internal/assets/card-roundup/mark-collected")
    void markRoundupCollected(@RequestBody List<Long> cardTransactionIds);

    @GetMapping("/internal/assets/points")
    PointSummary getAvailablePoints(@RequestParam Long userId,
                                     @RequestParam Long linkedAccountId);
}
